package com.networkcourse;

import com.networkcourse.models.GeocodeResponse;
import com.networkcourse.models.InterestingPlaceInfo;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    @FXML
    public TextField textFieldPlace;
    @FXML
    public ComboBox<String> comboBoxPlaces;
    @FXML
    public Button buttonPlaceSearch;
    @FXML
    public ListView<InterestingPlaceInfo> listViewInterestingPlaces;
    @FXML
    public Label weatherLabel;

    ObservableList<GeocodeResponse.GeocodePoint> comboBoxChoicesList = FXCollections.observableArrayList();
    ObservableMap<String, InterestingPlaceInfo> interestingPlacesMap = FXCollections.observableHashMap();

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        weatherLabel.setWrapText(true);
        setListViewCellFactory();

        LOGGER.info("Setting combo box choices list listener");
        comboBoxChoicesList.addListener(this::setComboBoxChoiceListener);

        LOGGER.info("Setting combo box places listener");
        comboBoxPlaces.valueProperty().addListener(this::setComboBoxPlacesListener);

        LOGGER.info("Setting interesting places map listener");
        interestingPlacesMap.addListener(this::interestingPlacesMapListener);
    }

    private void setListViewCellFactory() {
        listViewInterestingPlaces.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(InterestingPlaceInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    setMinWidth(param.getWidth());
                    setMaxWidth(param.getWidth());
                    setPrefWidth(param.getWidth());
                    setWrapText(true);
                    setText(item.toString());
                }
            }
        });
    }

    private void setComboBoxChoiceListener(ListChangeListener.Change<? extends GeocodeResponse.GeocodePoint> change) {
        while (change.next()) {
            if (change.wasAdded()) {
                Platform.runLater(() -> {
                    change.getAddedSubList().forEach(e -> comboBoxPlaces.getItems().add(e.toString()));
                    LOGGER.info("Combo box added items");
                });
            }
            if (change.wasRemoved()) {
                Platform.runLater(() -> {
                    change.getRemoved().forEach(e -> comboBoxPlaces.getItems().remove(e.toString()));
                    LOGGER.info("Combo box removed items");
                });
            }
        }
    }

    private void setComboBoxPlacesListener(ObservableValue<? extends String> observable, Object oldValue, Object newValue) {
        if (newValue == null) {
            return;
        }

        interestingPlacesMap.clear();
        GeocodeResponse.GeocodePoint chosenPlace = getChosenGeocodePoint(newValue.toString());

        Main.getRequestManager().getInterestingPlacesAround(chosenPlace)
                .thenAccept(placesList -> {
                    placesList.forEach(e -> interestingPlacesMap.put(e.xid, new InterestingPlaceInfo(e)));
                    LOGGER.info("Added {} interesting places to map", placesList.size());
                });

        Main.getRequestManager().getWeather(chosenPlace).thenAccept(weather -> {
            if (weather.weather == null) {
                LOGGER.error("Can't get weather info! Weather will not be updated!");
            }
            else {
                LOGGER.info("Got weather info: {}. Temp: {}°C", weather.weather.get(0).main, weather.main.temp);
                Platform.runLater(() -> weatherLabel.setText("Weather: " + weather.weather.get(0).main +
                        ". Temp: " + weather.main.temp + "°C"));
            }
        });
    }

    private void interestingPlacesMapListener(
            MapChangeListener.Change<? extends String, ? extends InterestingPlaceInfo> change) {
        if (change.wasRemoved()) {
            Platform.runLater(() -> {
                listViewInterestingPlaces.getItems().remove(change.getValueRemoved());
                LOGGER.info("List view removed {} option", change.getValueRemoved().toString());
            });
        }
        if (change.wasAdded()) {
            Platform.runLater(() -> {
                listViewInterestingPlaces.getItems().add(change.getValueAdded());
                LOGGER.info("List view added {} option", change.getValueAdded().toString());
            });
        }
    }

    @NonNull
    private GeocodeResponse.GeocodePoint getChosenGeocodePoint(String placeName) {
        for (GeocodeResponse.GeocodePoint i : comboBoxChoicesList) {
            if (placeName.equals(i.name + ", " + i.city) || placeName.equals(i.name)) {
                return i;
            }
        }
        return null;
    }

    public void btnPlaceSearchAction() {
        comboBoxChoicesList.clear();
        if (textFieldPlace.getText() != null && !textFieldPlace.getText().equals("")) {
            LOGGER.info("Text in textField is not empty, going to do a request");
            Main.getRequestManager().getPlaces(textFieldPlace.getText()).thenAccept(comboBoxChoicesList::addAll);
        } else {
            LOGGER.warn("Text in textField is empty");
        }
    }

    public void handleMouseClick(MouseEvent event) {
        if (listViewInterestingPlaces.getSelectionModel().getSelectedItem() == null) {
            return;
        }
        LOGGER.info("Clicked on ListView item: {}", listViewInterestingPlaces.getSelectionModel().getSelectedItem());
        if (listViewInterestingPlaces.getSelectionModel().getSelectedItem().triedGetInfo) {
            LOGGER.info("Already tried to get info for \"{}\"", listViewInterestingPlaces.getSelectionModel().getSelectedItem());
            return;
        }

        LOGGER.info("No attempts to get info for \"{}\", going to make a request",
                listViewInterestingPlaces.getSelectionModel().getSelectedItem());
        Main.getRequestManager().addPlaceInfo(listViewInterestingPlaces.getSelectionModel().getSelectedItem())
                .thenAccept(ignored -> Platform.runLater(() -> listViewInterestingPlaces.refresh()));
    }
}