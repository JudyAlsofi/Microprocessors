package com.tomasulo;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainApp extends Application {
    private final SimulatorConfig cfg = new SimulatorConfig();
    private TomasuloEngine engine;

    private TextArea logArea = new TextArea();
    private TableView<String> instrTable = new TableView<>();
    private TableView<Map.Entry<String, Integer>> registerTable = new TableView<>();
    private TableView<Map<String, Object>> cacheTable = new TableView<>();
    private TableView<Map<String, Object>> memoryTable = new TableView<>();
    private TableView<Map<String, Object>> finishCycleTable = new TableView<>();
    private Label cycleLabel = new Label("Cycle: 0");
    private Label cacheStatsLabel = new Label("Cache: Hits=0 Misses=0");
    
    // Track instruction finish cycles (station name -> finish info)
    private Map<String, Map<String, Object>> instructionHistory = new HashMap<>();
    
    // Station box containers
    private VBox addStationsBox = new VBox(5);
    private VBox mulStationsBox = new VBox(5);
    private VBox intStationsBox = new VBox(5);
    private VBox loadStationsBox = new VBox(5);
    private VBox finishTimesBox = new VBox(0);
    
    // Config fields
    private TextField addLatencyField, mulLatencyField, divLatencyField, loadLatencyField, storeLatencyField, intLatencyField;
    private TextField cacheSizeField, blockSizeField, hitLatencyField, missPenaltyField;
    private TextField addStationsField, mulStationsField, intStationsField, loadBuffersField;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Tomasulo Simulator");
        engine = new TomasuloEngine(cfg);

        BorderPane root = new BorderPane();
        
        // Top: controls and config
        VBox topBox = new VBox(10);
        topBox.setPadding(new Insets(10));
        
        // Control buttons
        HBox controls = new HBox(10);
        Button loadBtn = new Button("Load File");
        loadBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            File f = fc.showOpenDialog(primaryStage);
            if (f != null) loadFromFile(f);
        });

        Button stepBtn = new Button("Step Cycle");
        stepBtn.setOnAction(e -> {
            engine.step();
            refreshUI();
        });

        Button run10 = new Button("Run 10");
        run10.setOnAction(e -> {
            for (int i = 0; i < 10; i++) engine.step();
            refreshUI();
        });
        
        Button resetBtn = new Button("Reset");
        resetBtn.setOnAction(e -> {
            applyConfig();
            engine = new TomasuloEngine(cfg);
            instructionHistory.clear(); // Clear instruction history on reset
            refreshUI();
        });
        
        Button initRegsBtn = new Button("Init Regs (TC1)");
        initRegsBtn.setOnAction(e -> {
            RegisterInitializer.initializeForTestCase1(engine.registers);
            refreshUI();
            log("Initialized registers for Test Case 1");
        });
        
        cacheStatsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        controls.getChildren().addAll(cycleLabel, new Separator(), loadBtn, stepBtn, run10, resetBtn, initRegsBtn, new Separator(), cacheStatsLabel);
        
        // Config panel
        TitledPane configPane = new TitledPane();
        configPane.setText("Configuration");
        configPane.setCollapsible(true);
        configPane.setExpanded(false);
        
        GridPane configGrid = new GridPane();
        configGrid.setHgap(10);
        configGrid.setVgap(5);
        configGrid.setPadding(new Insets(10));
        
        int row = 0;
        configGrid.add(new Label("Add Latency:"), 0, row);
        addLatencyField = new TextField(String.valueOf(cfg.addLatency));
        addLatencyField.setPrefWidth(60);
        configGrid.add(addLatencyField, 1, row);
        
        configGrid.add(new Label("Mul Latency:"), 2, row);
        mulLatencyField = new TextField(String.valueOf(cfg.mulLatency));
        mulLatencyField.setPrefWidth(60);
        configGrid.add(mulLatencyField, 3, row++);
        
        configGrid.add(new Label("Div Latency:"), 0, row);
        divLatencyField = new TextField(String.valueOf(cfg.divLatency));
        divLatencyField.setPrefWidth(60);
        configGrid.add(divLatencyField, 1, row);
        
        configGrid.add(new Label("Load Latency:"), 2, row);
        loadLatencyField = new TextField(String.valueOf(cfg.loadLatency));
        loadLatencyField.setPrefWidth(60);
        configGrid.add(loadLatencyField, 3, row++);

        configGrid.add(new Label("Store Latency:"), 2, row);
        storeLatencyField = new TextField(String.valueOf(cfg.storeLatency));
        storeLatencyField.setPrefWidth(60);
        configGrid.add(storeLatencyField, 3, row++);

        configGrid.add(new Label("Int Latency:"), 0, row);
        intLatencyField = new TextField(String.valueOf(cfg.intLatency));
        intLatencyField.setPrefWidth(60);
        configGrid.add(intLatencyField, 1, row++);
        
        configGrid.add(new Label("Cache Size (B):"), 0, row);
        cacheSizeField = new TextField(String.valueOf(cfg.cacheSizeBytes));
        cacheSizeField.setPrefWidth(80);
        configGrid.add(cacheSizeField, 1, row);
        
        configGrid.add(new Label("Block Size (B):"), 2, row);
        blockSizeField = new TextField(String.valueOf(cfg.blockSizeBytes));
        blockSizeField.setPrefWidth(60);
        configGrid.add(blockSizeField, 3, row++);
        
        configGrid.add(new Label("Hit Latency:"), 0, row);
        hitLatencyField = new TextField(String.valueOf(cfg.cacheHitLatency));
        hitLatencyField.setPrefWidth(60);
        configGrid.add(hitLatencyField, 1, row);
        
        configGrid.add(new Label("Miss Penalty:"), 2, row);
        missPenaltyField = new TextField(String.valueOf(cfg.cacheMissPenalty));
        missPenaltyField.setPrefWidth(60);
        configGrid.add(missPenaltyField, 3, row++);
        
        configGrid.add(new Label("Add Stations:"), 0, row);
        addStationsField = new TextField(String.valueOf(cfg.numAddStations));
        addStationsField.setPrefWidth(60);
        configGrid.add(addStationsField, 1, row);
        
        configGrid.add(new Label("Mul Stations:"), 2, row);
        mulStationsField = new TextField(String.valueOf(cfg.numMulStations));
        mulStationsField.setPrefWidth(60);
        configGrid.add(mulStationsField, 3, row++);
        
        configGrid.add(new Label("Int Stations:"), 0, row);
        intStationsField = new TextField(String.valueOf(cfg.numIntStations));
        intStationsField.setPrefWidth(60);
        configGrid.add(intStationsField, 1, row);
        
        configGrid.add(new Label("Load Buffers:"), 2, row);
        loadBuffersField = new TextField(String.valueOf(cfg.numLoadBuffers));
        loadBuffersField.setPrefWidth(60);
        configGrid.add(loadBuffersField, 3, row++);
        
        Button applyBtn = new Button("Apply Config");
        applyBtn.setOnAction(e -> {
            applyConfig();
            engine = new TomasuloEngine(cfg);
            refreshUI();
        });
        configGrid.add(applyBtn, 0, row, 2, 1);
        
        configPane.setContent(configGrid);
        topBox.getChildren().addAll(controls, configPane);
        root.setTop(topBox);

        // Center: Main layout with all stations visible
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        
        VBox mainContent = new VBox(15);
        mainContent.setPadding(new Insets(15));
        
        // Top row: Instruction Queue and Registers side by side
        HBox topRow = new HBox(15);
        topRow.setPrefHeight(450);
        
        // Instruction Queue
        VBox instrBox = new VBox(5);
        Label instrLabel = new Label("Instruction Queue");
        instrLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        instrTable.setItems(FXCollections.observableArrayList());
        instrTable.setPrefHeight(400);
        instrTable.setMaxHeight(400);
        TableColumn<String, String> instrCol = new TableColumn<>("Instruction");
        instrCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()));
        instrCol.setPrefWidth(300);
        instrTable.getColumns().add(instrCol);
        instrBox.getChildren().addAll(instrLabel, instrTable);
        instrBox.setPrefWidth(350);
        
        // Registers
        VBox regBox = new VBox(5);
        Label regLabel = new Label("Register File");
        regLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        registerTable.setItems(FXCollections.observableArrayList());
        registerTable.setPrefHeight(400);
        registerTable.setMaxHeight(400);
        TableColumn<Map.Entry<String, Integer>, String> regNameCol = new TableColumn<>("Register");
        regNameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getKey()));
        regNameCol.setPrefWidth(80);
        TableColumn<Map.Entry<String, Integer>, String> regValCol = new TableColumn<>("Value");
        regValCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getValue())));
        regValCol.setPrefWidth(100);
        TableColumn<Map.Entry<String, Integer>, String> regTagCol = new TableColumn<>("Qi");
        regTagCol.setCellValueFactory(d -> {
            String regName = d.getValue().getKey();
            String tag = engine.registers.getTag(regName);
            return new javafx.beans.property.SimpleStringProperty(tag != null ? tag : "");
        });
        regTagCol.setPrefWidth(80);
        registerTable.getColumns().addAll(regNameCol, regValCol, regTagCol);
        regBox.getChildren().addAll(regLabel, registerTable);
        regBox.setPrefWidth(280);
        
        // Finish Cycle Display - styled like reservation stations
        VBox finishBox = new VBox(8);
        Label finishLabel = new Label("Instruction Finish Times");
        finishLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        finishLabel.setTextFill(Color.DARKBLUE);
        
        // Create header with column separators
        HBox finishHeader = new HBox(0);
        finishHeader.setPadding(new Insets(5));
        finishHeader.setStyle("-fx-background-color: #E0E0E0; -fx-border-color: #999; -fx-border-width: 1;");
        
        String[] finishHeaders = {"Station", "Instruction", "Finish", "Status"};
        int[] finishColWidths = {70, 150, 80, 90};
        
        for (int i = 0; i < finishHeaders.length; i++) {
            VBox headerCell = new VBox();
            headerCell.setPrefWidth(finishColWidths[i]);
            headerCell.setAlignment(Pos.CENTER);
            headerCell.setStyle("-fx-border-color: #999; -fx-border-width: 0 1 0 0; -fx-padding: 5;");
            Label h = new Label(finishHeaders[i]);
            h.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            h.setAlignment(Pos.CENTER);
            headerCell.getChildren().add(h);
            finishHeader.getChildren().add(headerCell);
        }
        
        // Create scrollable container for finish times
        ScrollPane finishScrollPane = new ScrollPane();
        finishScrollPane.setPrefHeight(370);
        finishScrollPane.setMaxHeight(370);
        finishScrollPane.setFitToWidth(true);
        finishScrollPane.setStyle("-fx-background-color: white;");
        
        finishScrollPane.setContent(finishTimesBox);
        
        finishBox.getChildren().addAll(finishLabel, finishHeader, finishScrollPane);
        finishBox.setPrefWidth(400);

        // Make sure all tables have the same settings
        instrTable.setPrefHeight(250);
        registerTable.setPrefHeight(250);
        finishCycleTable.setPrefHeight(250);

        // Set grow priority for all tables so they expand equally
        VBox.setVgrow(instrTable, Priority.ALWAYS);
        VBox.setVgrow(registerTable, Priority.ALWAYS);
        VBox.setVgrow(finishCycleTable, Priority.ALWAYS);

        // Make sure the HBox distributes space to all VBoxes equally
        HBox.setHgrow(instrBox, Priority.ALWAYS);
        HBox.setHgrow(regBox, Priority.ALWAYS);
        HBox.setHgrow(finishBox, Priority.ALWAYS);
        
        topRow.getChildren().addAll(instrBox, regBox, finishBox);
        
        // Middle: Reservation Stations in a grid
        GridPane stationsGrid = new GridPane();
        stationsGrid.setHgap(15);
        stationsGrid.setVgap(15);
        stationsGrid.setPadding(new Insets(10));
        
        // Add/Sub Stations
        VBox addSection = createStationSection("Add/Sub Stations", addStationsBox);
        stationsGrid.add(addSection, 0, 0);
        
        // Mul/Div Stations
        VBox mulSection = createStationSection("Mul/Div Stations", mulStationsBox);
        stationsGrid.add(mulSection, 1, 0);
        
        // Integer Stations
        VBox intSection = createStationSection("Integer Stations", intStationsBox);
        stationsGrid.add(intSection, 0, 1);
        
        // Load/Store Buffers
        VBox loadSection = createStationSection("Load/Store Buffers", loadStationsBox);
        stationsGrid.add(loadSection, 1, 1);
        
        // Cache Display
        VBox cacheSection = createCacheSection();
        stationsGrid.add(cacheSection, 2, 0); // First row, third column
        
        // Memory Display
        VBox memorySection = createMemorySection();
        stationsGrid.add(memorySection, 2, 1); // Second row, third column
        
        mainContent.getChildren().addAll(topRow, stationsGrid);
        scrollPane.setContent(mainContent);
        root.setCenter(scrollPane);

        // Bottom: log area
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);
        root.setBottom(logArea);

        Scene scene = new Scene(root, 1200, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
        refreshUI();
    }
    
    private VBox createStationSection(String title, VBox stationsContainer) {
        VBox section = new VBox(8);
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.DARKBLUE);
        
        // Create header table with column separators
        HBox header = new HBox(0);
        header.setPadding(new Insets(5));
        header.setStyle("-fx-background-color: #E0E0E0; -fx-border-color: #999; -fx-border-width: 1;");
        
        String[] headers = {"Name", "Busy", "Op", "Vj", "Vk", "Qj", "Qk", "Rem"};
        int[] colWidths = {60, 50, 70, 60, 60, 60, 60, 50};
        
        for (int i = 0; i < headers.length; i++) {
            VBox headerCell = new VBox();
            headerCell.setPrefWidth(colWidths[i]);
            headerCell.setAlignment(Pos.CENTER);
            headerCell.setStyle("-fx-border-color: #999; -fx-border-width: 0 1 0 0; -fx-padding: 5;");
            Label h = new Label(headers[i]);
            h.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            h.setAlignment(Pos.CENTER);
            headerCell.getChildren().add(h);
            header.getChildren().add(headerCell);
        }
        
        section.getChildren().addAll(titleLabel, header, stationsContainer);
        return section;
    }
    
    private HBox createStationBox(Map<String, Object> stationData) {
        HBox box = new HBox(0);
        box.setPadding(new Insets(5));
        box.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #999; -fx-border-width: 1;");
        
        String name = stationData.get("name") != null ? stationData.get("name").toString() : "";
        boolean busy = stationData.get("busy") != null && Boolean.parseBoolean(stationData.get("busy").toString());
        String inst = stationData.get("inst") != null ? stationData.get("inst").toString() : "";
        String vj = stationData.get("vj") != null ? stationData.get("vj").toString() : "";
        String vk = stationData.get("vk") != null ? stationData.get("vk").toString() : "";
        String qj = stationData.get("qj") != null ? stationData.get("qj").toString() : "";
        String qk = stationData.get("qk") != null ? stationData.get("qk").toString() : "";
        String rem = stationData.get("remaining") != null ? stationData.get("remaining").toString() : "0";
        
        // Extract op from instruction if available
        String op = "";
        if (!inst.isEmpty() && inst.contains(" ")) {
            op = inst.split("\\s+")[0];
        }
        
        // Column widths matching header
        int[] colWidths = {60, 50, 70, 60, 60, 60, 60, 50};
        String[] values = {name, busy ? "Yes" : "No", op, vj, vk, qj, qk, rem};
        Color[] colors = {
            Color.BLACK, // name
            busy ? Color.RED : Color.BLACK, // busy
            Color.BLACK, // op
            Color.BLACK, // vj
            Color.BLACK, // vk
            !qj.isEmpty() ? Color.BLUE : Color.BLACK, // qj
            !qk.isEmpty() ? Color.BLUE : Color.BLACK, // qk
            Color.BLACK  // rem
        };
        
        // Create cells with borders for each column
        for (int i = 0; i < values.length; i++) {
            VBox cell = new VBox();
            cell.setPrefWidth(colWidths[i]);
            cell.setAlignment(Pos.CENTER);
            cell.setStyle("-fx-border-color: #999; -fx-border-width: 0 1 0 0; -fx-padding: 5;");
            
            Label label = new Label(values[i]);
            if (i == 0) { // Name column
                label.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            } else {
                label.setFont(Font.font("Arial", 11));
            }
            label.setTextFill(colors[i]);
            label.setAlignment(Pos.CENTER);
            cell.getChildren().add(label);
            box.getChildren().add(cell);
        }
        
        return box;
    }
    
    private HBox createFinishTimeRow(Map<String, Object> finishData) {
        HBox box = new HBox(0);
        box.setPadding(new Insets(5));
        box.setStyle("-fx-background-color: #F5F5F5; -fx-border-color: #999; -fx-border-width: 1;");
        
        String station = finishData.get("station") != null ? finishData.get("station").toString() : "";
        String instruction = finishData.get("instruction") != null ? finishData.get("instruction").toString() : "";
        String finish = finishData.get("finishCycle") != null ? finishData.get("finishCycle").toString() : "---";
        String status = finishData.get("status") != null ? finishData.get("status").toString() : "---";
        
        // Column widths matching header
        int[] colWidths = {70, 150, 80, 90};
        String[] values = {station, instruction, finish, status};
        Color[] colors = {
            Color.BLACK, // station
            Color.BLACK, // instruction
            Color.BLACK, // finish
            "Completed".equals(status) ? Color.GREEN : Color.ORANGE // status
        };
        
        // Create cells with borders for each column
        for (int i = 0; i < values.length; i++) {
            VBox cell = new VBox();
            cell.setPrefWidth(colWidths[i]);
            cell.setAlignment(Pos.CENTER);
            cell.setStyle("-fx-border-color: #999; -fx-border-width: 0 1 0 0; -fx-padding: 5;");
            
            Label label = new Label(values[i]);
            if (i == 0) { // Station column
                label.setFont(Font.font("Arial", FontWeight.BOLD, 11));
            } else {
                label.setFont(Font.font("Arial", 11));
            }
            label.setTextFill(colors[i]);
            label.setAlignment(Pos.CENTER);
            cell.getChildren().add(label);
            box.getChildren().add(cell);
        }
        
        return box;
    }
    
    private VBox createCacheSection() {
        VBox section = new VBox(8);
        Label titleLabel = new Label("Data Cache");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.DARKBLUE);
        
        // Create cache table
        cacheTable.setPrefHeight(500);
        cacheTable.setMaxHeight(500);
        cacheTable.setPrefWidth(350);
        
        // Index column
        TableColumn<Map<String, Object>, String> indexCol = new TableColumn<>("Index");
        indexCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("index") != null ? d.getValue().get("index").toString() : ""));
        indexCol.setPrefWidth(50);
        
        // Valid column
        TableColumn<Map<String, Object>, String> validCol = new TableColumn<>("Valid");
        validCol.setCellValueFactory(d -> {
            boolean valid = d.getValue().get("valid") != null && Boolean.parseBoolean(d.getValue().get("valid").toString());
            return new javafx.beans.property.SimpleStringProperty(valid ? "Yes" : "No");
        });
        validCol.setPrefWidth(50);
        
        // Tag column
        TableColumn<Map<String, Object>, String> tagCol = new TableColumn<>("Tag");
        tagCol.setCellValueFactory(d -> {
            Object tag = d.getValue().get("tag");
            return new javafx.beans.property.SimpleStringProperty(
                tag != null && !tag.toString().equals("-1") ? tag.toString() : "---");
        });
        tagCol.setPrefWidth(60);
        
        // Address range column
        TableColumn<Map<String, Object>, String> addrCol = new TableColumn<>("Address Range");
        addrCol.setCellValueFactory(d -> {
            Object base = d.getValue().get("baseAddr");
            Object end = d.getValue().get("endAddr");
            if (base != null && !base.toString().equals("-1")) {
                return new javafx.beans.property.SimpleStringProperty(
                    base.toString() + "-" + end.toString());
            }
            return new javafx.beans.property.SimpleStringProperty("---");
        });
        addrCol.setPrefWidth(120);
        
        // Data sample column
        TableColumn<Map<String, Object>, String> dataCol = new TableColumn<>("Data (hex)");
        dataCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("data") != null ? d.getValue().get("data").toString() : "---"));
        dataCol.setPrefWidth(100);
        
        cacheTable.getColumns().addAll(indexCol, validCol, tagCol, addrCol, dataCol);
        
        section.getChildren().addAll(titleLabel, cacheTable);
        return section;
    }
    
    private VBox createMemorySection() {
        VBox section = new VBox(8);
        Label titleLabel = new Label("Memory (Non-Zero)");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        titleLabel.setTextFill(Color.DARKBLUE);
        
        // Create memory table
        memoryTable.setPrefHeight(500);
        memoryTable.setMaxHeight(500);
        memoryTable.setPrefWidth(350);
        
        // Address column
        TableColumn<Map<String, Object>, String> addrCol = new TableColumn<>("Address");
        addrCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("address") != null ? d.getValue().get("address").toString() : ""));
        addrCol.setPrefWidth(80);
        
        // Value column (decimal)
        TableColumn<Map<String, Object>, String> valueCol = new TableColumn<>("Value (Dec)");
        valueCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("value") != null ? d.getValue().get("value").toString() : ""));
        valueCol.setPrefWidth(100);
        
        // Value column (hex)
        TableColumn<Map<String, Object>, String> hexCol = new TableColumn<>("Value (Hex)");
        hexCol.setCellValueFactory(d -> {
            Object val = d.getValue().get("value");
            if (val != null) {
                try {
                    int value = Integer.parseInt(val.toString());
                    return new javafx.beans.property.SimpleStringProperty(String.format("0x%08X", value));
                } catch (NumberFormatException e) {
                    return new javafx.beans.property.SimpleStringProperty("---");
                }
            }
            return new javafx.beans.property.SimpleStringProperty("---");
        });
        hexCol.setPrefWidth(120);
        
        memoryTable.getColumns().addAll(addrCol, valueCol, hexCol);
        
        section.getChildren().addAll(titleLabel, memoryTable);
        return section;
    }
    
    private void applyConfig() {
        try {
            cfg.addLatency = Integer.parseInt(addLatencyField.getText());
            cfg.mulLatency = Integer.parseInt(mulLatencyField.getText());
            cfg.divLatency = Integer.parseInt(divLatencyField.getText());
            cfg.loadLatency = Integer.parseInt(loadLatencyField.getText());
            cfg.storeLatency = Integer.parseInt(storeLatencyField.getText());
            cfg.intLatency = Integer.parseInt(intLatencyField.getText());
            cfg.cacheSizeBytes = Integer.parseInt(cacheSizeField.getText());
            cfg.blockSizeBytes = Integer.parseInt(blockSizeField.getText());
            cfg.cacheHitLatency = Integer.parseInt(hitLatencyField.getText());
            cfg.cacheMissPenalty = Integer.parseInt(missPenaltyField.getText());
            cfg.numAddStations = Integer.parseInt(addStationsField.getText());
            cfg.numMulStations = Integer.parseInt(mulStationsField.getText());
            cfg.numIntStations = Integer.parseInt(intStationsField.getText());
            cfg.numLoadBuffers = Integer.parseInt(loadBuffersField.getText());
        } catch (NumberFormatException ex) {
            log("Invalid config value: " + ex.getMessage());
        }
    }

    private void loadFromFile(File f) {
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            List<String> lines = new ArrayList<>();
            Map<String, Integer> labels = new HashMap<>();
            String line;
            
            // First pass: collect lines and build label map
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                // Check for label
                if (line.contains(":")) {
                    String label = line.substring(0, line.indexOf(':')).trim();
                    labels.put(label, lines.size()); // Map label to instruction index
                }
                lines.add(line);
            }
            
            // Second pass: parse instructions with label resolution
            List<Instruction> ins = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                Instruction parsed = parse(lines.get(i), labels, i);
                if (parsed != null) ins.add(parsed);
            }
            
            engine.loadInstructions(ins);
            log("Loaded " + ins.size() + " instructions from " + f.getName());
            refreshUI();
        } catch (Exception ex) {
            ex.printStackTrace();
            log("Error loading file: " + ex.getMessage());
        }
    }

    private Instruction parse(String line, Map<String, Integer> labels, int currentIndex) {
        // very simple parser for forms like: L.D F6, 0(R2)
        try {
            String raw = line;
            
            // Strip label if present (e.g., "LOOP: L.D F0, 8(R1)")
            if (line.contains(":")) {
                line = line.substring(line.indexOf(':') + 1).trim();
            }
            
            String op = line.split("\\s+")[0].replaceAll("\\.", "_").toUpperCase();
            String rest = line.substring(line.indexOf(' ') + 1).trim();
            String[] parts = rest.split(",");
            InstructionType type = InstructionType.valueOf(op);
            switch (type) {
                case LD: case LW: case L_D: case L_S:
                    String dest = parts[0].trim();
                    String addr = parts[1].trim();
                    // form: offset(Rx)
                    int p = addr.indexOf('(');
                    int imm = Integer.parseInt(addr.substring(0, p).trim());
                    String base = addr.substring(p + 1, addr.indexOf(')')).trim();
                    return new Instruction(type, dest, base, null, imm, raw);
                case SD: case SW: case S_S: case S_D: case S_W:
                    // src, offset(Rx)
                    String src = parts[0].trim();
                    String addr2 = parts[1].trim();
                    int p2 = addr2.indexOf('(');
                    int imm2 = Integer.parseInt(addr2.substring(0, p2).trim());
                    String base2 = addr2.substring(p2 + 1, addr2.indexOf(')')).trim();
                    return new Instruction(type, null, base2, src, imm2, raw);
                case ADD: case SUB: case MUL: case DIV:
                case ADD_D: case SUB_D: case MUL_D: case DIV_D:
                case ADD_S: case SUB_S: case MUL_S: case DIV_S:
                    // dest, src1, src2
                    String d = parts[0].trim();
                    String s1 = parts[1].trim();
                    String s2 = parts[2].trim();
                    return new Instruction(type, d, s1, s2, null, raw);
                case ADDI: case SUBI: case DADDI: case DSUBI:
                    // dest, src, imm
                    String dd = parts[0].trim();
                    String ss = parts[1].trim();
                    int imm3 = Integer.parseInt(parts[2].trim());
                    return new Instruction(type, dd, ss, null, imm3, raw);
                case BEQ: case BNE:
                    // BEQ R1, R2, offset or BEQ R1, R2, LABEL
                    String bb0 = parts[0].trim();
                    String bb1 = parts[1].trim();
                    String offsetStr = parts[2].trim();
                    int off;
                    
                    // Check if it's a label or numeric offset
                    if (labels.containsKey(offsetStr)) {
                        // Calculate relative offset: target - (current + 1)
                        off = labels.get(offsetStr) - (currentIndex + 1);
                    } else {
                        // Direct numeric offset
                        off = Integer.parseInt(offsetStr);
                    }
                    
                    return new Instruction(type, null, bb0, bb1, off, raw);
                default:
                    return new Instruction(type, null, null, null, null, raw);
            }
        } catch (Exception ex) {
            log("Failed parsing line: " + line + " -> " + ex.getMessage());
            return null;
        }
    }

    private void refreshUI() {
        Map<String, Object> snapshot = engine.snapshot();
        
        // Update cycle label
        cycleLabel.setText("Cycle: " + snapshot.get("cycle"));
        
        // Update cache statistics
        cacheStatsLabel.setText(String.format("Cache: Hits=%d Misses=%d", 
            engine.cache.getHits(), engine.cache.getMisses()));
        
        // Update instruction queue
        instrTable.setItems(FXCollections.observableArrayList());
        @SuppressWarnings("unchecked")
        List<Instruction> queue = (List<Instruction>) snapshot.get("instrQueue");
        for (Instruction inst : queue) {
            instrTable.getItems().add(inst.toString());
        }
        
        // Update reservation stations - populate boxes
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addList = (List<Map<String, Object>>) snapshot.get("addStations");
        addStationsBox.getChildren().clear();
        for (Map<String, Object> station : addList) {
            addStationsBox.getChildren().add(createStationBox(station));
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mulList = (List<Map<String, Object>>) snapshot.get("mulStations");
        mulStationsBox.getChildren().clear();
        for (Map<String, Object> station : mulList) {
            mulStationsBox.getChildren().add(createStationBox(station));
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intList = (List<Map<String, Object>>) snapshot.get("intStations");
        intStationsBox.getChildren().clear();
        for (Map<String, Object> station : intList) {
            intStationsBox.getChildren().add(createStationBox(station));
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> loadList = (List<Map<String, Object>>) snapshot.get("loadBuffers");
        loadStationsBox.getChildren().clear();
        for (Map<String, Object> station : loadList) {
            loadStationsBox.getChildren().add(createStationBox(station));
        }
        
        // Update cache table
        List<Map<String, Object>> cacheState = engine.cache.getCacheState();
        cacheTable.setItems(FXCollections.observableArrayList(cacheState));
        
        // Update memory table (show non-zero words only)
        List<Map<String, Object>> memoryState = engine.cache.getMemoryState();
        memoryTable.setItems(FXCollections.observableArrayList(memoryState));
        
        // Update finish cycle table - keep history of all instructions
        int currentCycle = engine.cycle;
        
        // Collect all busy stations
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allStations = new ArrayList<>();
        allStations.addAll(addList);
        allStations.addAll(mulList);
        allStations.addAll(intList);
        allStations.addAll(loadList);
        
        // Update history with current busy stations
        for (Map<String, Object> station : allStations) {
            boolean busy = station.get("busy") != null && Boolean.parseBoolean(station.get("busy").toString());
            String stationName = station.get("name") != null ? station.get("name").toString() : "";
            
            if (busy && station.get("inst") != null) {
                // Update or add instruction to history
                Map<String, Object> finishInfo = instructionHistory.getOrDefault(stationName, new HashMap<>());
                finishInfo.put("station", stationName);
                finishInfo.put("instruction", station.get("inst"));
                finishInfo.put("status", "Executing");
                
                // Calculate finish cycle: current cycle + remaining cycles
                Object remObj = station.get("remaining");
                if (remObj != null) {
                    try {
                        int remaining = Integer.parseInt(remObj.toString());
                        int finishCycle = currentCycle + remaining;
                        finishInfo.put("finishCycle", finishCycle);
                    } catch (NumberFormatException e) {
                        finishInfo.put("finishCycle", "---");
                    }
                } else {
                    finishInfo.put("finishCycle", "---");
                }
                instructionHistory.put(stationName, finishInfo);
            } else if (!busy && instructionHistory.containsKey(stationName)) {
                // Station just finished - mark as completed
                Map<String, Object> finishInfo = instructionHistory.get(stationName);
                if (!"Completed".equals(finishInfo.get("status"))) {
                    // Mark as completed, keep the finish cycle from when it finished
                    finishInfo.put("status", "Completed");
                    // If we have a finish cycle, keep it; otherwise set to current cycle
                    if (finishInfo.get("finishCycle") == null || "---".equals(finishInfo.get("finishCycle"))) {
                        finishInfo.put("finishCycle", currentCycle);
                    }
                }
            }
        }
        
        // Convert history to list for display (sorted by station name)
        List<Map<String, Object>> finishCycles = new ArrayList<>(instructionHistory.values());
        finishCycles.sort((a, b) -> {
            String nameA = a.get("station") != null ? a.get("station").toString() : "";
            String nameB = b.get("station") != null ? b.get("station").toString() : "";
            return nameA.compareTo(nameB);
        });
        
        // Update finish times display with styled rows
        finishTimesBox.getChildren().clear();
        for (Map<String, Object> finishData : finishCycles) {
            finishTimesBox.getChildren().add(createFinishTimeRow(finishData));
        }
        
        // Update registers (show all 32 of each type)
        @SuppressWarnings("unchecked")
        Map<String, Integer> regs = (Map<String, Integer>) snapshot.get("registers");
        @SuppressWarnings("unchecked")
        Map<String, String> regTags = (Map<String, String>) snapshot.get("registerTags");
        List<Map.Entry<String, Integer>> regList = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            String rName = "R" + i;
            if (regs.containsKey(rName)) regList.add(new java.util.AbstractMap.SimpleEntry<>(rName, regs.get(rName)));
        }
        for (int i = 0; i < 32; i++) {
            String fName = "F" + i;
            if (regs.containsKey(fName)) regList.add(new java.util.AbstractMap.SimpleEntry<>(fName, regs.get(fName)));
        }
        registerTable.setItems(FXCollections.observableArrayList(regList));
        registerTable.refresh(); // Force refresh to update tag column
        
        // Update log
        logArea.clear();
        int start = Math.max(0, engine.history.size() - 50); // last 50 lines
        for (int i = start; i < engine.history.size(); i++) {
            logArea.appendText(engine.history.get(i) + "\n");
        }
        logArea.setScrollTop(Double.MAX_VALUE);
    }

    private void log(String s) {
        engine.history.add(s);
        refreshUI();
    }

    public static void main(String[] args) {
        launch(args);
    }
}