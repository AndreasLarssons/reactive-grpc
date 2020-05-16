package demo.backpressure;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import numbers.proto.NumbersProto;
import numbers.proto.RxBackpressureDemoGrpc;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class BackpressureController extends RxBackpressureDemoGrpc.BackpressureDemoImplBase {
    @FXML
    public LineChart<Long, Integer> lineChart;
    @FXML
    public Label producedLabel;
    @FXML
    public Label consumedLabel;
    @FXML
    public NumberAxis timeDimension;
    @FXML
    public Button startButton;

    private XYChart.Series<Long, Integer> producedSeries = new XYChart.Series<>();
    private XYChart.Series<Long, Integer> consumedSeries = new XYChart.Series<>();

    private RxBackpressureDemoGrpc.RxBackpressureDemoStub stub;




    /**
     * Slowly request numbers
     */
//    @FXML
//    public void startBackpressure(ActionEvent actionEvent) {
//        resetGraph();
//        Single.just(5000)
//                // Construct request
//                .map(i -> NumbersProto.HowMany.newBuilder().setNumber(i).build())
//                // Call service
//                .as(stub::sendNumbers)
//                // Parse response
//                .map(i -> i.getNumber(0))
//                // Introduce a synthetic three millisecond delay per read
//                .zipWith(Flowable.interval(3, TimeUnit.MILLISECONDS), (item, interval) -> item)
//                // Transition processing to UI thread
//                .observeOn(JavaFxScheduler.platform())
//                // Execute
//                .subscribe(
//                        i -> {
//                            consumedLabel.setText(i.toString());
//                            consumedSeries.getData().add(new XYChart.Data<>(System.currentTimeMillis(), i));
//                        },
//                        Throwable::printStackTrace,
//                        () -> startButton.setDisable(false)
//                );
//    }

    @FXML
    public void startBackpressure(ActionEvent actionEvent) {
        resetGraph();
        Flowable.range(0,5000)
                .map(BackpressureController::howManyNum)
                .flatMapSingle(stub::sendNumber)
                // Parse response
                .map(i -> i.getNumber(0))
                .onBackpressureBuffer()
                // Introduce a synthetic three millisecond delay per read
//                .zipWith(Flowable.interval(3, TimeUnit.MILLISECONDS), (item, interval) -> item)
                // Transition processing to UI thread
                .observeOn(JavaFxScheduler.platform())
                // Execute
                .subscribe(
                        i -> {
                            consumedLabel.setText(i.toString());
                            consumedSeries.getData().add(new XYChart.Data<>(System.currentTimeMillis(), i));
                            Thread.sleep(3);
                        },
                        Throwable::printStackTrace,
                        () -> startButton.setDisable(false)
                );
    }


    /**
     * Quickly produce numbers
     */
    @Override
    public Flowable<NumbersProto.Number> sendNumbers(Single<NumbersProto.HowMany> request) {
        // Fork the response flowable using share()
        Flowable<Integer> numbers = request
                // Extract request
                .map(r -> r.getNumber(0))
                // Process request
                .flatMapPublisher(i -> Flowable.range(0, i))
                .share();

        // One fork updates the UI
        numbers.observeOn(JavaFxScheduler.platform())
                .subscribe(i -> {
                    producedLabel.setText(i.toString());
                    producedSeries.getData().add(new XYChart.Data<>(System.currentTimeMillis(), i));
                });

        // Other fork returns the number stream
        return numbers.map(BackpressureController::protoNum);
    }

    @Override
    public Single<NumbersProto.Number> sendNumber(Single<NumbersProto.HowMany> request) {
        // Fork the response flowable using share()
        Flowable<Integer> numbers = request
                // Extract request
                .map(r -> r.getNumber(0))
                // Process request
                .toFlowable()
                .share();

        // One fork updates the UI
        numbers.observeOn(JavaFxScheduler.platform())
                .subscribe(i -> {
                    producedLabel.setText(i.toString());
                    producedSeries.getData().add(new XYChart.Data<>(System.currentTimeMillis(), i));
                });

        // Other fork returns the number stream
        return numbers.map(BackpressureController::protoNum).firstOrError();
    }

    @FXML
    public void initialize() throws Exception {
        Server server = ServerBuilder.forPort(9000).addService(this).build().start();
        Channel channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
        stub = RxBackpressureDemoGrpc.newRxStub(channel);

        producedSeries.setName("Produced");
        consumedSeries.setName("Consumed");
        lineChart.getData().add(producedSeries);
        lineChart.getData().add(consumedSeries);
    }

    private static NumbersProto.HowMany howManyNum(int i) {
        Integer[] ints = new Integer[1024];
        Arrays.fill(ints, i);
        return NumbersProto.HowMany.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }

    private static NumbersProto.Number protoNum(int i) {
        Integer[] ints = new Integer[1024];
        Arrays.fill(ints, i);
        return NumbersProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }

    private void resetGraph() {
        startButton.setDisable(true);
        timeDimension.setLowerBound(System.currentTimeMillis());
        producedSeries.getData().clear();
        consumedSeries.getData().clear();
    }
}
