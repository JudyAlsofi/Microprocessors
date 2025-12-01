package com.tomasulo;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainApp extends Application {
    private final SimulatorConfig cfg = new SimulatorConfig();
    private TomasuloEngine engine;

    private TextArea logArea = new TextArea();
    private TableView<String> instrTable = new TableView<>();
    private TableView<Map<String, Object>> addStationTable = new TableView<>();
    private TableView<Map<String, Object>> mulStationTable = new TableView<>();
    private TableView<Map<String, Object>> intStationTable = new TableView<>();
    private TableView<Map<String, Object>> loadBufferTable = new TableView<>();
    private TableView<Map.Entry<String, Integer>> registerTable = new TableView<>();
    private Label cycleLabel = new Label("Cycle: 0");
    private Label cacheStatsLabel = new Label("Cache: Hits=0 Misses=0");
    
    // Config fields
    private TextField addLatencyField, mulLatencyField, divLatencyField, loadLatencyField;
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
            refreshUI();
        });
        
        Button initRegsBtn = new Button("Init Regs (TC1)");
        initRegsBtn.setOnAction(e -> {
            RegisterInitializer.initializeForTestCase1(engine.registers);
            refreshUI();
            log("Initialized registers for Test Case 1");
        });
        
        Label cacheStatsLabel = new Label("Cache: Hits=0 Misses=0");
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

        // Center: TabPane with tables
        TabPane tabPane = new TabPane();
        
        // Instructions tab
        Tab instrTab = new Tab("Instructions");
        instrTab.setClosable(false);
        instrTable.setItems(FXCollections.observableArrayList());
        TableColumn<String, String> instrCol = new TableColumn<>("Instruction Queue");
        instrCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()));
        instrCol.setPrefWidth(300);
        instrTable.getColumns().add(instrCol);
        instrTab.setContent(instrTable);
        
        // Add Stations tab
        Tab addTab = new Tab("Add/Sub Stations");
        addTab.setClosable(false);
        addStationTable = createStationTable();
        addTab.setContent(addStationTable);
        
        // Mul Stations tab
        Tab mulTab = new Tab("Mul/Div Stations");
        mulTab.setClosable(false);
        mulStationTable = createStationTable();
        mulTab.setContent(mulStationTable);
        
        // Int Stations tab
        Tab intTab = new Tab("Int Stations");
        intTab.setClosable(false);
        intStationTable = createStationTable();
        intTab.setContent(intStationTable);
        
        // Load Buffers tab
        Tab loadTab = new Tab("Load/Store Buffers");
        loadTab.setClosable(false);
        loadBufferTable = createStationTable();
        loadTab.setContent(loadBufferTable);
        
        // Registers tab
        Tab regTab = new Tab("Registers");
        regTab.setClosable(false);
        registerTable.setItems(FXCollections.observableArrayList());
        TableColumn<Map.Entry<String, Integer>, String> regNameCol = new TableColumn<>("Register");
        regNameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getKey()));
        regNameCol.setPrefWidth(100);
        TableColumn<Map.Entry<String, Integer>, String> regValCol = new TableColumn<>("Value");
        regValCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.valueOf(d.getValue().getValue())));
        regValCol.setPrefWidth(100);
        registerTable.getColumns().add(regNameCol);
        registerTable.getColumns().add(regValCol);
        regTab.setContent(registerTable);
        
        tabPane.getTabs().addAll(instrTab, addTab, mulTab, intTab, loadTab, regTab);
        root.setCenter(tabPane);

        // Bottom: log area
        logArea.setEditable(false);
        logArea.setPrefRowCount(8);
        root.setBottom(logArea);

        Scene scene = new Scene(root, 1200, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
        refreshUI();
    }
    
    private TableView<Map<String, Object>> createStationTable() {
        TableView<Map<String, Object>> table = new TableView<>();
        
        TableColumn<Map<String, Object>, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("name") != null ? d.getValue().get("name").toString() : ""));
        nameCol.setPrefWidth(80);
        
        TableColumn<Map<String, Object>, String> busyCol = new TableColumn<>("Busy");
        busyCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("busy") != null ? d.getValue().get("busy").toString() : "false"));
        busyCol.setPrefWidth(50);
        
        TableColumn<Map<String, Object>, String> instCol = new TableColumn<>("Instruction");
        instCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("inst") != null ? d.getValue().get("inst").toString() : ""));
        instCol.setPrefWidth(150);
        
        TableColumn<Map<String, Object>, String> vjCol = new TableColumn<>("Vj");
        vjCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("vj") != null ? d.getValue().get("vj").toString() : ""));
        vjCol.setPrefWidth(60);
        
        TableColumn<Map<String, Object>, String> vkCol = new TableColumn<>("Vk");
        vkCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("vk") != null ? d.getValue().get("vk").toString() : ""));
        vkCol.setPrefWidth(60);
        
        TableColumn<Map<String, Object>, String> qjCol = new TableColumn<>("Qj");
        qjCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("qj") != null ? d.getValue().get("qj").toString() : ""));
        qjCol.setPrefWidth(60);
        
        TableColumn<Map<String, Object>, String> qkCol = new TableColumn<>("Qk");
        qkCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("qk") != null ? d.getValue().get("qk").toString() : ""));
        qkCol.setPrefWidth(60);
        
        TableColumn<Map<String, Object>, String> remCol = new TableColumn<>("Remaining");
        remCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().get("remaining") != null ? d.getValue().get("remaining").toString() : "0"));
        remCol.setPrefWidth(80);
        
        table.getColumns().add(nameCol);
        table.getColumns().add(busyCol);
        table.getColumns().add(instCol);
        table.getColumns().add(vjCol);
        table.getColumns().add(vkCol);
        table.getColumns().add(qjCol);
        table.getColumns().add(qkCol);
        table.getColumns().add(remCol);
        return table;
    }
    
    private void applyConfig() {
        try {
            cfg.addLatency = Integer.parseInt(addLatencyField.getText());
            cfg.mulLatency = Integer.parseInt(mulLatencyField.getText());
            cfg.divLatency = Integer.parseInt(divLatencyField.getText());
            cfg.loadLatency = Integer.parseInt(loadLatencyField.getText());
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
            List<Instruction> ins = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                Instruction parsed = parse(line);
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

    private Instruction parse(String line) {
        // very simple parser for forms like: L.D F6, 0(R2)
        try {
            String raw = line;
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
                case SD: case SW: case S_D: case S_W:
                    // src, offset(Rx)
                    String src = parts[0].trim();
                    String addr2 = parts[1].trim();
                    int p2 = addr2.indexOf('(');
                    int imm2 = Integer.parseInt(addr2.substring(0, p2).trim());
                    String base2 = addr2.substring(p2 + 1, addr2.indexOf(')')).trim();
                    return new Instruction(type, null, base2, src, imm2, raw);
                case ADD: case SUB: case MUL: case DIV:
                case ADD_D: case SUB_D: case MUL_D: case DIV_D:
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
                    // BEQ R1, R2, offset
                    String bb0 = parts[0].trim();
                    String bb1 = parts[1].trim();
                    int off = Integer.parseInt(parts[2].trim());
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
        
        // Update reservation stations
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> addList = (List<Map<String, Object>>) snapshot.get("addStations");
        addStationTable.setItems(FXCollections.observableArrayList(addList));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mulList = (List<Map<String, Object>>) snapshot.get("mulStations");
        mulStationTable.setItems(FXCollections.observableArrayList(mulList));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> intList = (List<Map<String, Object>>) snapshot.get("intStations");
        intStationTable.setItems(FXCollections.observableArrayList(intList));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> loadList = (List<Map<String, Object>>) snapshot.get("loadBuffers");
        loadBufferTable.setItems(FXCollections.observableArrayList(loadList));
        
        // Update registers (show first 10 of each type)
        @SuppressWarnings("unchecked")
        Map<String, Integer> regs = (Map<String, Integer>) snapshot.get("registers");
        List<Map.Entry<String, Integer>> regList = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String rName = "R" + i;
            if (regs.containsKey(rName)) regList.add(new java.util.AbstractMap.SimpleEntry<>(rName, regs.get(rName)));
        }
        for (int i = 0; i < 8; i++) {
            String fName = "F" + i;
            if (regs.containsKey(fName)) regList.add(new java.util.AbstractMap.SimpleEntry<>(fName, regs.get(fName)));
        }
        registerTable.setItems(FXCollections.observableArrayList(regList));
        
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