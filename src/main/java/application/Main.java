package application;

import javafx.application.Application;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook; 

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;


public class Main extends Application {
   // ========== ENCRYPTION UTILITY CLASS (NEW) ==========
    
    /**
     * Simple encryption utility for password storage using AES-128
     */
    static class PasswordEncryption {
        private static final String ALGORITHM = "AES";
        private SecretKey secretKey;
        
        /**
         * Constructor - loads or creates encryption key
         * @param keyPath Path to store the encryption key
         */
        public PasswordEncryption(Path keyPath) throws Exception {
            if (Files.exists(keyPath)) {
                // Load existing key
                byte[] keyBytes = Files.readAllBytes(keyPath);
                secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            } else {
                // Generate new key
                KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
                keyGen.init(128);
                secretKey = keyGen.generateKey();
                
                // Save key for future use
                Files.createDirectories(keyPath.getParent());
                Files.write(keyPath, secretKey.getEncoded());
            }
        }
        
        /**
         * Encrypt plaintext password
         * @param plainText The password to encrypt
         * @return Base64 encoded encrypted password
         */
        public String encrypt(String plainText) throws Exception {
            if (plainText == null || plainText.isEmpty()) {
                return "";
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        }
        
        /**
         * Decrypt encrypted password
         * @param encryptedText Base64 encoded encrypted password
         * @return Decrypted plaintext password
         */
        public String decrypt(String encryptedText) throws Exception {
            if (encryptedText == null || encryptedText.isEmpty()) {
                return "";
            }
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted, "UTF-8");
        }
    }
    
    // ========== SQL TAB CLASS ==========
    
    /**
     * Inner class to represent a SQL query tab with its own components
     */
    class SQLTab {
        Tab tab;
        TextArea textArea;
        TableView<ObservableList<StringProperty>> table;
        ProgressIndicator progressIndicator;
        Label statusLabel;
        HBox buttonBox;
        Button executeButton;
        Button resetButton;
        Button downloadButton;
        String[][] resultData;
        
        SQLTab(String tabName) {
            // Create tab
            tab = new Tab(tabName);
            tab.setClosable(true);
            
            // Create text area for SQL
            textArea = new TextArea();
            textArea.setEditable(true);
            textArea.setPrefHeight(200);
            textArea.setPrefWidth(1000);
            textArea.setPromptText("Enter your SQL query here... (Press F12 or Ctrl+Enter to execute - connection auto-validates)");
            
            // Add key event handler
            textArea.setOnKeyPressed(event -> handleKeyPress(event));
            
            // Create table for results
            table = new TableView<>();
            VBox.setVgrow(table, Priority.ALWAYS);
            
            // ========== PERFORMANCE OPTIMIZATION: Configure table settings ==========
            table.setFixedCellSize(25.0);  // Improves rendering for large datasets
            table.setTableMenuButtonVisible(false);  // Disable unnecessary features
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            
            // Create progress indicator
            progressIndicator = new ProgressIndicator();
            progressIndicator.setVisible(false);
            progressIndicator.setPrefSize(50, 50);
            
            // Create status label
            statusLabel = new Label();
            statusLabel.setFont(new Font("Arial", 12));
            statusLabel.setTextFill(Color.BLUE);
            
            // Create buttons
            executeButton = new Button("Execute");
            executeButton.setOnAction(event -> executeCurrentLine());
            
            resetButton = new Button("Reset");
            resetButton.setOnAction(event -> {
                textArea.setText("");
                table.getItems().clear();
                table.getColumns().clear();
                statusLabel.setText("");
            });
            
            downloadButton = new Button("Download");
            downloadButton.setOnAction(event -> downloadToExcel(this));
            
            buttonBox = new HBox(10);
            buttonBox.getChildren().addAll(executeButton, resetButton, downloadButton);
            
            // Layout
            VBox content = new VBox(5);
            content.setPadding(new Insets(10));
            content.getChildren().addAll(
                textArea,
                buttonBox,
                statusLabel,
                progressIndicator,
                table
            );
            
            tab.setContent(content);
        }
        
        private void handleKeyPress(KeyEvent event) {
            // F12 - Execute selected text
            if (event.getCode() == KeyCode.F12) {
                String selectedText = textArea.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    executeSQLQueryAsync(selectedText, this);
                }
            }
            // Ctrl+Enter - Execute selected text or current line
            else if (event.getCode() == KeyCode.ENTER && event.isControlDown()) {
                String selectedText = textArea.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    executeSQLQueryAsync(selectedText, this);
                } else {
                    executeCurrentLine();
                }
                event.consume();
            }
        }
        
        private void executeCurrentLine() {
            int caretPos = textArea.getCaretPosition();
            int previousNewline = textArea.getText().lastIndexOf('\n', caretPos - 1);
            int nextNewline = textArea.getText().indexOf('\n', caretPos);
            if (nextNewline == -1) nextNewline = textArea.getText().length();
            
            int startPos = previousNewline + 1;
            int endPos = nextNewline;
            
            if (startPos < endPos) {
                String currentLine = textArea.getText().substring(startPos, endPos).trim();
                if (!currentLine.isEmpty()) {
                    executeSQLQueryAsync(currentLine, this);
                }
            }
        }
    }
    
    // ========== SAVED CONNECTION CLASS (MODIFIED) ==========
    
    /**
     * Connection class to store connection details with ENCRYPTED password
     */
    static class SavedConnection implements Serializable {
        private static final long serialVersionUID = 2L; // Changed from 1L
        private String name;
        private String hostname;
        private String username;
        private String encryptedPassword; // CHANGED: was 'password'
        
        public SavedConnection(String name, String hostname, String username, String encryptedPassword) {
            this.name = name;
            this.hostname = hostname;
            this.username = username;
            this.encryptedPassword = encryptedPassword;
        }
        
        public String getName() { return name; }
        public String getHostname() { return hostname; }
        public String getUsername() { return username; }
        public String getEncryptedPassword() { return encryptedPassword; }
        
        public void setName(String name) { this.name = name; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public void setUsername(String username) { this.username = username; }
        public void setEncryptedPassword(String encryptedPassword) {
            this.encryptedPassword = encryptedPassword;
        }
        
        @Override
        public String toString() {
            return name + " (" + hostname + ")";
        }
    }
    
    // ========== INSTANCE VARIABLES ==========
    
    private TabPane tabPane = new TabPane();
    private int tabCounter = 1;
    private List<SQLTab> sqlTabs = new ArrayList<>();  // Track all SQLTab instances
    
    String[][] string2DArray;
    WebServiceClient obj = new WebServiceClient();
    String host = null;
    String username = null;
    String pwd = null;
    
    // UI Components
    private TextField hostText;
    private TextField usernameText;
    private PasswordField passwordText;  // FIXED: Changed from TextField to PasswordField
    
    // Saved connections
    private List<SavedConnection> savedConnections = new ArrayList<>();
    
    // NEW: Secure file paths
    private static final String APP_DIR_NAME = ".otc-sql-connect";
    private static final String CONNECTIONS_FILE = "saved_connections.dat";
    private static final String ENCRYPTION_KEY_FILE = "app.key";
    
    // NEW: Encryption manager
    private PasswordEncryption passwordEncryption;
    private Path appDirectory;
    private Path connectionsFilePath;
    
    // Menu items
    private Menu savedConnectionsMenu;
    
    // ========== MAIN METHOD ==========
    
    public static void main(String[] args) {
        launch(args);
    }
    
    // ========== APPLICATION START (MODIFIED) ==========
    
    @Override
    public void start(Stage primaryStage) {
        // NEW: Initialize encryption and secure file paths
        try {
        	System.out.println("App Directory: " + System.getProperty("user.home") + File.separator + ".otc-sql-connect");
            initializeAppDirectory();
            passwordEncryption = new PasswordEncryption(appDirectory.resolve(ENCRYPTION_KEY_FILE));
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Initialization Error", 
                "Failed to initialize encryption: " + e.getMessage() + 
                "\n\nThe application will continue but passwords will not be encrypted.");
            passwordEncryption = null; // Fallback mode
        }
        
        // Load saved connections
        loadConnections();
        
        Scene scene = new Scene(new Group());
        primaryStage.setTitle("OTC Cloud SQL Connect v1.2 (Optimized)");
        primaryStage.setWidth(1000);
        primaryStage.setHeight(600);
        
        final Label label = new Label("Address Book");
        label.setFont(new Font("Arial", 20));
        
        // Menu Bar
        MenuBar menuBar = new MenuBar();
        
        // FILE MENU (NEW)
        Menu menuFile = new Menu("File");
        MenuItem newTab = new MenuItem("New Tab");
        newTab.setOnAction(e -> addNewTab());
        SeparatorMenuItem fileSeparator = new SeparatorMenuItem();
        MenuItem closeTab = new MenuItem("Close Tab");
        closeTab.setOnAction(e -> closeCurrentTab());
        
        menuFile.getItems().addAll(newTab, closeTab);
        
        savedConnectionsMenu = new Menu("Saved Connections");
        Menu menuEdit = new Menu("Edit");
        Menu menuView = new Menu("View");
        Menu menuHelp = new Menu("Help");
        
        // Saved Connections Menu Items
        MenuItem addConnection = new MenuItem("Add New Connection...");
        MenuItem manageConnections = new MenuItem("Manage Connections...");
        SeparatorMenuItem separator = new SeparatorMenuItem();
        
        addConnection.setOnAction(e -> showAddConnectionDialog());
        manageConnections.setOnAction(e -> showManageConnectionsDialog());
        
        savedConnectionsMenu.getItems().addAll(addConnection, manageConnections, separator);
        
        // Populate saved connections menu
        updateSavedConnectionsMenu();
        
        // Edit Menu Items
        MenuItem clearQuery = new MenuItem("Clear Query");
        MenuItem clearResults = new MenuItem("Clear Results");
        
        clearQuery.setOnAction(e -> {
            SQLTab currentTab = getCurrentSQLTab();
            if (currentTab != null) {
                currentTab.textArea.setText("");
            }
        });
        clearResults.setOnAction(e -> {
            SQLTab currentTab = getCurrentSQLTab();
            if (currentTab != null) {
                currentTab.table.getItems().clear();
                currentTab.table.getColumns().clear();
            }
        });
        
        menuEdit.getItems().addAll(clearQuery, clearResults);
        
        // View Menu Items
        MenuItem increaseFontSize = new MenuItem("Increase Font Size");
        MenuItem decreaseFontSize = new MenuItem("Decrease Font Size");
        
        increaseFontSize.setOnAction(e -> {
            SQLTab currentTab = getCurrentSQLTab();
            if (currentTab != null) {
                Font currentFont = currentTab.textArea.getFont();
                currentTab.textArea.setFont(new Font(currentFont.getFamily(), currentFont.getSize() + 2));
            }
        });
        
        decreaseFontSize.setOnAction(e -> {
            SQLTab currentTab = getCurrentSQLTab();
            if (currentTab != null) {
                Font currentFont = currentTab.textArea.getFont();
                currentTab.textArea.setFont(new Font(currentFont.getFamily(), Math.max(8, currentFont.getSize() - 2)));
            }
        });
        
        menuView.getItems().addAll(increaseFontSize, decreaseFontSize);
        
        // Help Menu Items
        MenuItem about = new MenuItem("About");
        MenuItem shortcuts = new MenuItem("Keyboard Shortcuts");
        
        about.setOnAction(e -> showAboutDialog());
        shortcuts.setOnAction(e -> showShortcutsDialog());
        
        menuHelp.getItems().addAll(shortcuts, about);
        
        menuBar.getMenus().addAll(menuFile, savedConnectionsMenu, menuEdit, menuView, menuHelp);
        
        // Instance Details Box
        HBox HInstanceDetailsBox = new HBox();
        
        // Labels
        Label hostLabel = new Label("Hostname");
        Label usernameLabel = new Label("UserName");
        Label pwdLabel = new Label("Password");
        
        // Text Fields
        hostText = new TextField();
        usernameText = new TextField();
        passwordText = new PasswordField();  // FIXED: Changed from TextField to PasswordField
        
        // Check Credentials Button (Optional - auto-validates on first query)
        Button checkCredentialsButton = new Button("Test Connection");
        checkCredentialsButton.setOnAction(event -> {
            checkCredentialsAsync(hostText.getText(), usernameText.getText(), passwordText.getText());
        });
        
        HInstanceDetailsBox.setSpacing(10);
        hostText.setPrefWidth(300);
        HInstanceDetailsBox.getChildren().addAll(
            hostLabel, hostText, 
            usernameLabel, usernameText, 
            pwdLabel, passwordText, 
            checkCredentialsButton
        );
        
        // Create first tab
        addNewTab();
        
        // Layout
        final VBox vbox = new VBox();
        vbox.setSpacing(5);
        vbox.setPadding(new Insets(10, 0, 0, 10));
        vbox.getChildren().addAll(
            menuBar, 
            HInstanceDetailsBox,
            tabPane
        );
        
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        
        Scene scene1 = new Scene(new StackPane(vbox));
        scene1.setFill(Color.CORAL);
        primaryStage.setScene(scene1);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(700);
        primaryStage.show();
    }
    
    // ========== NEW METHOD: Initialize App Directory ==========
    
    /**
     * Initialize application directory in user's home folder
     * Creates ~/.otc-sql-connect/ directory for secure file storage
     */
    private void initializeAppDirectory() throws IOException {
        String userHome = System.getProperty("user.home");
        appDirectory = Paths.get(userHome, APP_DIR_NAME);
        connectionsFilePath = appDirectory.resolve(CONNECTIONS_FILE);
        
        // Create directory if it doesn't exist
        if (!Files.exists(appDirectory)) {
            Files.createDirectories(appDirectory);
            System.out.println("Created app directory: " + appDirectory);
        }
    }
    
    // ========== TAB MANAGEMENT METHODS ==========
    
    /**
     * Add a new SQL query tab
     */
    private void addNewTab() {
        SQLTab sqlTab = new SQLTab("Query " + tabCounter++);
        sqlTabs.add(sqlTab);  // Track the instance
        tabPane.getTabs().add(sqlTab.tab);
        tabPane.getSelectionModel().select(sqlTab.tab);
    }
    
    /**
     * Close the current tab
     */
    private void closeCurrentTab() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null && tabPane.getTabs().size() > 1) {
            // Remove from tracking list
            sqlTabs.removeIf(sqlTab -> sqlTab.tab == selectedTab);
            tabPane.getTabs().remove(selectedTab);
        } else if (tabPane.getTabs().size() == 1) {
            showAlert(AlertType.INFORMATION, "Cannot Close", "Cannot close the last tab.");
        }
    }
    
    /**
     * Get the current SQLTab object
     */
    private SQLTab getCurrentSQLTab() {
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            // Find the SQLTab instance by tab reference
            for (SQLTab sqlTab : sqlTabs) {
                if (sqlTab.tab == selectedTab) {
                    return sqlTab;
                }
            }
        }
        return null;
    }
    
    /**
     * Find SQLTab object by Tab reference (deprecated - use getCurrentSQLTab instead)
     */
    private SQLTab findSQLTabByTab(Tab tab) {
        for (SQLTab sqlTab : sqlTabs) {
            if (sqlTab.tab == tab) {
                return sqlTab;
            }
        }
        return null;
    }
    
    // ========== MODIFIED: Add Connection Dialog with Encryption ==========
    
    /**
     * Show dialog to add a new connection
     * MODIFIED: Now encrypts password before saving
     */
    private void showAddConnectionDialog() {
        Dialog<SavedConnection> dialog = new Dialog<>();
        dialog.setTitle("Add New Connection");
        dialog.setHeaderText("Enter connection details");
        
        // Set button types
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField nameField = new TextField();
        nameField.setPromptText("Connection Name");
        TextField hostnameField = new TextField();
        hostnameField.setPromptText("https://hostname.oraclecloud.com");
        TextField usernameField = new TextField();
        usernameField.setPromptText("username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("password");
        
        grid.add(new Label("Connection Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Hostname:"), 0, 1);
        grid.add(hostnameField, 1, 1);
        grid.add(new Label("Username:"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(new Label("Password:"), 0, 3);
        grid.add(passwordField, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        // Request focus on name field
        Platform.runLater(() -> nameField.requestFocus());
        
        // Enable/disable save button based on input
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);
        
        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            saveButton.setDisable(newValue.trim().isEmpty() || hostnameField.getText().trim().isEmpty());
        });
        
        hostnameField.textProperty().addListener((observable, oldValue, newValue) -> {
            saveButton.setDisable(newValue.trim().isEmpty() || nameField.getText().trim().isEmpty());
        });
        
        // Convert result when save button clicked
        // MODIFIED: Encrypt password before creating connection
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    String encryptedPwd = "";
                    if (passwordEncryption != null) {
                        encryptedPwd = passwordEncryption.encrypt(passwordField.getText());
                    } else {
                        // Fallback: store as-is if encryption failed
                        encryptedPwd = passwordField.getText();
                    }
                    
                    return new SavedConnection(
                        nameField.getText().trim(),
                        hostnameField.getText().trim(),
                        usernameField.getText().trim(),
                        encryptedPwd
                    );
                } catch (Exception e) {
                    showAlert(AlertType.ERROR, "Encryption Error", 
                        "Failed to encrypt password: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });
        
        Optional<SavedConnection> result = dialog.showAndWait();
        
        result.ifPresent(connection -> {
            if (connection != null) {
                savedConnections.add(connection);
                saveConnections();
                updateSavedConnectionsMenu();
                showAlert(AlertType.INFORMATION, "Success", 
                    "Connection '" + connection.getName() + "' saved with encrypted password!");
            }
        });
    }
    
    // ========== Manage Connections Dialog (Unchanged) ==========
    
    /**
     * Show dialog to manage (update/delete) connections
     */
    private void showManageConnectionsDialog() {
        if (savedConnections.isEmpty()) {
            showAlert(AlertType.INFORMATION, "No Connections", "No saved connections to manage.");
            return;
        }
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Manage Connections");
        dialog.setHeaderText("Select a connection to edit or delete");
        
        // Create list view
        ListView<SavedConnection> listView = new ListView<>();
        listView.getItems().addAll(savedConnections);
        listView.setPrefHeight(300);
        
        // Buttons
        HBox buttonBox = new HBox(10);
        Button editButton = new Button("Edit");
        Button deleteButton = new Button("Delete");
        Button closeButton = new Button("Close");
        
        editButton.setDisable(true);
        deleteButton.setDisable(true);
        
        // Enable buttons when item selected
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editButton.setDisable(!hasSelection);
            deleteButton.setDisable(!hasSelection);
        });
        
        // Edit button action
        editButton.setOnAction(e -> {
            SavedConnection selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showEditConnectionDialog(selected);
                listView.refresh();
                updateSavedConnectionsMenu();
            }
        });
        
        // Delete button action
        deleteButton.setOnAction(e -> {
            SavedConnection selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert confirmAlert = new Alert(AlertType.CONFIRMATION);
                confirmAlert.setTitle("Confirm Delete");
                confirmAlert.setHeaderText("Delete Connection");
                confirmAlert.setContentText("Are you sure you want to delete '" + selected.getName() + "'?");
                
                Optional<ButtonType> result = confirmAlert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    savedConnections.remove(selected);
                    listView.getItems().remove(selected);
                    saveConnections();
                    updateSavedConnectionsMenu();
                    showAlert(AlertType.INFORMATION, "Deleted", "Connection deleted successfully!");
                }
            }
        });
        
        // Close button action
        closeButton.setOnAction(e -> dialog.close());
        
        buttonBox.getChildren().addAll(editButton, deleteButton, closeButton);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(listView, buttonBox);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        dialog.showAndWait();
    }
    
    // ========== MODIFIED: Edit Connection Dialog with Encryption ==========
    
    /**
     * Show dialog to edit existing connection
     * MODIFIED: Decrypt password for display, encrypt on save
     */
    private void showEditConnectionDialog(SavedConnection connection) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Connection");
        dialog.setHeaderText("Edit connection details");
        
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        TextField nameField = new TextField(connection.getName());
        TextField hostnameField = new TextField(connection.getHostname());
        TextField usernameField = new TextField(connection.getUsername());
        PasswordField passwordField = new PasswordField();
        
        // MODIFIED: Decrypt password for editing
        try {
            if (passwordEncryption != null) {
                String decryptedPwd = passwordEncryption.decrypt(connection.getEncryptedPassword());
                passwordField.setText(decryptedPwd);
            } else {
                passwordField.setText(connection.getEncryptedPassword());
            }
        } catch (Exception e) {
            System.err.println("Failed to decrypt password: " + e.getMessage());
            passwordField.setPromptText("Enter new password");
        }
        
        grid.add(new Label("Connection Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Hostname:"), 0, 1);
        grid.add(hostnameField, 1, 1);
        grid.add(new Label("Username:"), 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(new Label("Password:"), 0, 3);
        grid.add(passwordField, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        
        // MODIFIED: Encrypt password on save
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    connection.setName(nameField.getText().trim());
                    connection.setHostname(hostnameField.getText().trim());
                    connection.setUsername(usernameField.getText().trim());
                    
                    if (!passwordField.getText().isEmpty()) {
                        String encryptedPwd = "";
                        if (passwordEncryption != null) {
                            encryptedPwd = passwordEncryption.encrypt(passwordField.getText());
                        } else {
                            encryptedPwd = passwordField.getText();
                        }
                        connection.setEncryptedPassword(encryptedPwd);
                    }
                    
                    return true;
                } catch (Exception e) {
                    showAlert(AlertType.ERROR, "Encryption Error", 
                        "Failed to encrypt password: " + e.getMessage());
                    return false;
                }
            }
            return false;
        });
        
        Optional<Boolean> result = dialog.showAndWait();
        
        if (result.isPresent() && result.get()) {
            saveConnections();
            showAlert(AlertType.INFORMATION, "Success", "Connection updated successfully!");
        }
    }
    
    // ========== Connection Menu Methods ==========
    
    /**
     * Update saved connections menu with current connections
     */
    private void updateSavedConnectionsMenu() {
        // Remove all items after the separator
        while (savedConnectionsMenu.getItems().size() > 3) {
            savedConnectionsMenu.getItems().remove(3);
        }
        
        // Add saved connections
        if (!savedConnections.isEmpty()) {
            for (SavedConnection conn : savedConnections) {
                MenuItem item = new MenuItem(conn.getName());
                item.setOnAction(e -> loadConnection(conn));
                savedConnectionsMenu.getItems().add(item);
            }
        } else {
            MenuItem noConnections = new MenuItem("(No saved connections)");
            noConnections.setDisable(true);
            savedConnectionsMenu.getItems().add(noConnections);
        }
    }
    
    // ========== MODIFIED: Load Connection with Decryption ==========
    
    /**
     * Load a saved connection into the form
     * MODIFIED: Decrypt password before loading, password field now masks it
     */
    private void loadConnection(SavedConnection connection) {
        hostText.setText(connection.getHostname());
        usernameText.setText(connection.getUsername());
        
        // MODIFIED: Decrypt password before displaying
        try {
            if (passwordEncryption != null) {
                String decryptedPwd = passwordEncryption.decrypt(connection.getEncryptedPassword());
                passwordText.setText(decryptedPwd);  // Now masked automatically by PasswordField
            } else {
                passwordText.setText(connection.getEncryptedPassword());
            }
        } catch (Exception e) {
            showAlert(AlertType.ERROR, "Decryption Error", 
                "Failed to decrypt password: " + e.getMessage());
            passwordText.setText("");
        }
        
        // Show status in current tab if available
        SQLTab currentTab = getCurrentSQLTab();
        if (currentTab != null) {
            currentTab.statusLabel.setText("Loaded connection: " + connection.getName());
        }
    }
    
    // ========== MODIFIED: Save/Load Connections ==========
    
    /**
     * Save connections to file in secure user directory
     * MODIFIED: Uses secure file path
     */
    private void saveConnections() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(connectionsFilePath.toFile()))) {
            oos.writeObject(savedConnections);
            System.out.println("Connections saved to: " + connectionsFilePath);
        } catch (IOException e) {
            showAlert(AlertType.ERROR, "Save Error", 
                "Error saving connections: " + e.getMessage());
        }
    }
    
    /**
     * Load connections from file in secure user directory
     * MODIFIED: Uses secure file path and better error handling
     */
    @SuppressWarnings("unchecked")
    private void loadConnections() {
        if (connectionsFilePath == null || !Files.exists(connectionsFilePath)) {
            savedConnections = new ArrayList<>();
            return;
        }
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(connectionsFilePath.toFile()))) {
            savedConnections = (List<SavedConnection>) ois.readObject();
            System.out.println("Loaded " + savedConnections.size() + " connection(s)");
        } catch (IOException | ClassNotFoundException e) {
            savedConnections = new ArrayList<>();
            showAlert(AlertType.WARNING, "Load Warning", 
                "Could not load saved connections.\nError: " + e.getMessage());
        }
    }
    
    // ========== Dialog Methods ==========
    
    /**
     * Show About dialog
     */
    private void showAboutDialog() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("OTC Cloud SQL Connect");
        alert.setContentText(
            "Version 1.2 - With Optimized Table Performance\n\n" +
            "Features:\n" +
            "- Save multiple connections\n" +
            "- Multi-tab interface for parallel queries\n" +
            "- AES-128 encrypted password storage\n" +
            "- Masked password input for security\n" +
            "- Auto-validates connection on first query\n" +
            "- Execute SQL queries\n" +
            "- OPTIMIZED table rendering (500-5000 rows)\n" +
            "- Export results to Excel\n\n" +
            "Security:\n" +
            "- Passwords encrypted using AES-128\n" +
            "- Stored in: " + appDirectory + "\n\n" +
            "Performance:\n" +
            "- Background threading for smooth UI\n" +
            "- Batch updates for large result sets\n" +
            "- Fixed cell sizing for faster rendering"
        );
        alert.showAndWait();
    }
    
    /**
     * Show Keyboard Shortcuts dialog
     */
    private void showShortcutsDialog() {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Keyboard Shortcuts");
        alert.setHeaderText("Available Keyboard Shortcuts");
        alert.setContentText(
            "Query Execution:\n" +
            "  F12 - Execute selected text (or current line)\n" +
            "  Ctrl+Enter - Execute selected text (or current line)\n\n" +
            "Tab Management:\n" +
            "  File > New Tab - Open new query tab\n" +
            "  File > Close Tab - Close current tab\n\n" +
            "Editing:\n" +
            "  Ctrl+A - Select all\n" +
            "  Ctrl+C - Copy\n" +
            "  Ctrl+V - Paste\n" +
            "  Ctrl+Z - Undo"
        );
        alert.showAndWait();
    }
    
    // ========== Query Execution Methods (Unchanged) ==========
    
    /**
     * Check credentials asynchronously
     * This validates and stores connection for ALL tabs in the session
     */
    private void checkCredentialsAsync(String hostValue, String usernameValue, String pwdValue) {
        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Checking credentials...");
                return obj.checkCredentials(
                    hostValue + "/xmlpserver/services/v2/ReportService?WSDL",
                    usernameValue,
                    pwdValue
                );
            }
        };
        
        // Show progress in first tab if available
        SQLTab firstTab = getCurrentSQLTab();
        if (firstTab != null) {
            firstTab.progressIndicator.visibleProperty().bind(task.runningProperty());
            firstTab.statusLabel.textProperty().bind(task.messageProperty());
        }
        
        task.setOnSucceeded(event -> {
            String result = task.getValue();
            
            if ("SUCCESS".equals(result)) {
                host = hostValue;
                username = usernameValue;
                pwd = pwdValue;
                
                showAlert(AlertType.CONFIRMATION, "Success", "Connection successful! Ready to execute queries in all tabs.");
                
                if (firstTab != null) {
                    firstTab.statusLabel.textProperty().unbind();
                    firstTab.statusLabel.setText("Connection validated");
                }
            } else if ("FAULT".equals(result)) {
                showAlert(
                    AlertType.ERROR, 
                    "Error",
                    "Connection to " + hostValue + " failed. Please check the hostname and credentials"
                );
                
                if (firstTab != null) {
                    firstTab.statusLabel.textProperty().unbind();
                    firstTab.statusLabel.setText("Connection failed");
                }
            } else {
                showAlert(
                    AlertType.WARNING,
                    "Warning",
                    "Cloud Connect Report is not deployed"
                );
                
                if (firstTab != null) {
                    firstTab.statusLabel.textProperty().unbind();
                    firstTab.statusLabel.setText("");
                }
            }
        });
        
        task.setOnFailed(event -> {
            showAlert(AlertType.ERROR, "Error", "Connection check failed: " + task.getException().getMessage());
            
            if (firstTab != null) {
                firstTab.statusLabel.textProperty().unbind();
                firstTab.statusLabel.setText("Connection check failed");
            }
        });
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Execute SQL query asynchronously
     * AUTO-VALIDATES connection on first execution in session
     */
    private void executeSQLQueryAsync(String query, SQLTab sqlTab) {
        // Auto-validate connection if not already done
        if (host == null || username == null || pwd == null) {
            String hostValue = hostText.getText().trim();
            String usernameValue = usernameText.getText().trim();
            String pwdValue = passwordText.getText().trim();
            
            // Check if credentials are provided
            if (hostValue.isEmpty() || usernameValue.isEmpty() || pwdValue.isEmpty()) {
                showAlert(AlertType.WARNING, "Missing Credentials", 
                    "Please enter hostname, username, and password before executing queries.");
                return;
            }
            
            // Validate connection first, then execute query
            autoValidateAndExecute(hostValue, usernameValue, pwdValue, query, sqlTab);
            return;
        }
        
        // Connection already validated, execute directly
        executeQueryTask(query, sqlTab);
    }
    
    /**
     * Auto-validate connection and execute query on success
     */
    private void autoValidateAndExecute(String hostValue, String usernameValue, String pwdValue, String query, SQLTab sqlTab) {
        Task<String> validationTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                updateMessage("Validating connection...");
                return obj.checkCredentials(
                    hostValue + "/xmlpserver/services/v2/ReportService?WSDL",
                    usernameValue,
                    pwdValue
                );
            }
        };
        
        sqlTab.progressIndicator.visibleProperty().bind(validationTask.runningProperty());
        sqlTab.statusLabel.textProperty().bind(validationTask.messageProperty());
        
        sqlTab.executeButton.setDisable(true);
        sqlTab.downloadButton.setDisable(true);
        
        validationTask.setOnSucceeded(event -> {
            String result = validationTask.getValue();
            
            sqlTab.statusLabel.textProperty().unbind();
            
            if ("SUCCESS".equals(result)) {
                // Store validated credentials
                host = hostValue;
                username = usernameValue;
                pwd = pwdValue;
                
                sqlTab.statusLabel.setText("Connection validated. Executing query...");
                
                // Now execute the query
                executeQueryTask(query, sqlTab);
            } else if ("FAULT".equals(result)) {
                showAlert(
                    AlertType.ERROR, 
                    "Connection Failed",
                    "Connection to " + hostValue + " failed. Please check the hostname and credentials."
                );
                sqlTab.statusLabel.setText("Connection failed");
                sqlTab.executeButton.setDisable(false);
                sqlTab.downloadButton.setDisable(false);
            } else {
                showAlert(
                    AlertType.WARNING,
                    "Warning",
                    "Cloud Connect Report is not deployed"
                );
                sqlTab.statusLabel.setText("");
                sqlTab.executeButton.setDisable(false);
                sqlTab.downloadButton.setDisable(false);
            }
        });
        
        validationTask.setOnFailed(event -> {
            showAlert(AlertType.ERROR, "Validation Error", 
                "Connection validation failed: " + validationTask.getException().getMessage());
            sqlTab.statusLabel.textProperty().unbind();
            sqlTab.statusLabel.setText("Connection validation failed");
            sqlTab.executeButton.setDisable(false);
            sqlTab.downloadButton.setDisable(false);
        });
        
        Thread thread = new Thread(validationTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Execute the actual SQL query (separated from validation logic)
     */
    private void executeQueryTask(String query, SQLTab sqlTab) {
        Task<String[][]> task = new Task<String[][]>() {
            @Override
            protected String[][] call() throws Exception {
                updateMessage("Executing query...");
                updateProgress(0, 100);
                
                String[][] result = obj.postSOAPXML(query, host, username, pwd);
                
                updateProgress(100, 100);
                return result;
            }
        };
        
        sqlTab.progressIndicator.visibleProperty().bind(task.runningProperty());
        sqlTab.statusLabel.textProperty().bind(task.messageProperty());
        
        sqlTab.executeButton.setDisable(true);
        sqlTab.downloadButton.setDisable(true);
        
        task.setOnSucceeded(event -> {
            sqlTab.resultData = task.getValue();
            
            if (sqlTab.resultData != null && sqlTab.resultData.length > 0) {
                // ========== PERFORMANCE OPTIMIZATION: Use new optimized method ==========
                populateTableOptimized(sqlTab.resultData, sqlTab);
                
                int rowCount = sqlTab.resultData.length - 1;
                int colCount = sqlTab.resultData[0].length;
                
                sqlTab.statusLabel.textProperty().unbind();
                sqlTab.statusLabel.setText("Query executed successfully. Rows: " + rowCount + ", Columns: " + colCount);
            } else {
                sqlTab.statusLabel.textProperty().unbind();
                sqlTab.statusLabel.setText("No data returned");
            }
            
            sqlTab.executeButton.setDisable(false);
            sqlTab.downloadButton.setDisable(false);
        });
        
        task.setOnFailed(event -> {
            showAlert(AlertType.ERROR, "Error", "Query execution failed: " + task.getException().getMessage());
            
            sqlTab.statusLabel.textProperty().unbind();
            sqlTab.statusLabel.setText("Query execution failed");
            
            sqlTab.executeButton.setDisable(false);
            sqlTab.downloadButton.setDisable(false);
        });
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    // ========== OPTIMIZED TABLE POPULATION METHOD ==========
    
    /**
     * OPTIMIZED: Populate table with background threading and batch updates
     * This method dramatically improves performance for 500-5000 row datasets
     * 
     * @param data 2D array of data from SOAP service (first row is headers)
     * @param sqlTab The SQLTab to populate
     */
    private void populateTableOptimized(String[][] data, SQLTab sqlTab) {
        if (data == null || data.length == 0) {
            return;
        }
        
        // Clear table immediately on UI thread
        Platform.runLater(() -> {
            sqlTab.table.getItems().clear();
            sqlTab.table.getColumns().clear();
            sqlTab.table.setPlaceholder(new Label("Loading data..."));
        });
        
        // Create background task to process data
        Task<ObservableList<ObservableList<StringProperty>>> processTask = new Task<>() {
            @Override
            protected ObservableList<ObservableList<StringProperty>> call() throws Exception {
                updateMessage("Processing data...");
                
                int rowCount = data.length;
                int colCount = data[0].length;
                
                // Create all rows in background thread
                ObservableList<ObservableList<StringProperty>> allRows = FXCollections.observableArrayList();
                
                for (int i = 1; i < rowCount; i++) {
                    ObservableList<StringProperty> row = FXCollections.observableArrayList();
                    for (int j = 0; j < colCount; j++) {
                        row.add(new SimpleStringProperty(data[i][j]));
                    }
                    allRows.add(row);
                    
                    // Update progress
                    updateProgress(i, rowCount - 1);
                }
                
                return allRows;
            }
        };
        
        // Bind progress to status
        sqlTab.statusLabel.textProperty().bind(processTask.messageProperty());
        
        processTask.setOnSucceeded(event -> {
            // Get processed data
            ObservableList<ObservableList<StringProperty>> allRows = processTask.getValue();
            
            // Create columns on UI thread
            int colCount = data[0].length;
            for (int j = 0; j < colCount; j++) {
                sqlTab.table.getColumns().add(createColumn(j, data[0][j]));
            }
            
            // CRITICAL: Single batch update - all rows at once
            sqlTab.table.setItems(allRows);
            
            // Enable cell selection
            sqlTab.table.getSelectionModel().setCellSelectionEnabled(true);
            
            // Restore placeholder
            sqlTab.table.setPlaceholder(new Label("No data available"));
            
            // Unbind status
            sqlTab.statusLabel.textProperty().unbind();
            
            System.out.println("Table populated with " + allRows.size() + " rows in optimized mode");
        });
        
        processTask.setOnFailed(event -> {
            sqlTab.statusLabel.textProperty().unbind();
            sqlTab.statusLabel.setText("Error processing data");
            showAlert(AlertType.ERROR, "Processing Error", 
                "Failed to process table data: " + processTask.getException().getMessage());
        });
        
        // Execute in background thread
        Thread thread = new Thread(processTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Create table column
     */
    private TableColumn<ObservableList<StringProperty>, String> createColumn(
            final int columnIndex, String columnTitle) {
        
        TableColumn<ObservableList<StringProperty>, String> column = new TableColumn<>();
        String title;
        
        if (columnTitle == null || columnTitle.trim().length() == 0) {
            title = "Column " + (columnIndex + 1);
        } else {
            title = columnTitle;
        }
        
        column.setText(title);
        column.setCellValueFactory(
            new Callback<TableColumn.CellDataFeatures<ObservableList<StringProperty>, String>, ObservableValue<String>>() {
                @Override
                public ObservableValue<String> call(
                        CellDataFeatures<ObservableList<StringProperty>, String> cellDataFeatures) {
                    ObservableList<StringProperty> values = cellDataFeatures.getValue();
                    if (columnIndex >= values.size()) {
                        return new SimpleStringProperty("");
                    } else {
                        return values.get(columnIndex);
                    }
                }
            });
        
        return column;
    }
    
    /**
     * Download table data to Excel
     */
    private void downloadToExcel(SQLTab sqlTab) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Exporting to Excel...");
                
                HSSFWorkbook hssfWorkbook = new HSSFWorkbook();
                HSSFSheet hssfSheet = hssfWorkbook.createSheet("Sheet1");
                HSSFRow firstRow = hssfSheet.createRow(0);
                
                for (int i = 0; i < sqlTab.table.getColumns().size(); i++) {
                    short j = (short) i;
                    firstRow.createCell(j).setCellValue(sqlTab.table.getColumns().get(i).getText());
                }
                
                for (int row = 0; row < sqlTab.table.getItems().size(); row++) {
                    HSSFRow hssfRow = hssfSheet.createRow(row + 1);
                    for (int col = 0; col < sqlTab.table.getColumns().size(); col++) {
                        short colShort = (short) col;
                        Object celValue = sqlTab.table.getColumns().get(col).getCellObservableValue(row).getValue();
                        
                        try {
                            if (celValue != null && Double.parseDouble(celValue.toString()) != 0.0) {
                                hssfRow.createCell(colShort).setCellValue(Double.parseDouble(celValue.toString()));
                            }
                        } catch (NumberFormatException e) {
                            hssfRow.createCell(colShort).setCellValue(celValue != null ? celValue.toString() : "");
                        }
                    }
                    
                    updateProgress(row + 1, sqlTab.table.getItems().size());
                }
                
                FileOutputStream fileOut = new FileOutputStream("workbook.xls");
                hssfWorkbook.write(fileOut);
                fileOut.close();
                hssfWorkbook.close();
                
                return null;
            }
        };
        
        sqlTab.progressIndicator.visibleProperty().bind(task.runningProperty());
        sqlTab.statusLabel.textProperty().bind(task.messageProperty());
        sqlTab.downloadButton.setDisable(true);
        
        task.setOnSucceeded(event -> {
            sqlTab.statusLabel.textProperty().unbind();
            sqlTab.statusLabel.setText("Export completed: workbook.xls");
            sqlTab.downloadButton.setDisable(false);
            showAlert(AlertType.INFORMATION, "Success", "Data exported to workbook.xls");
        });
        
        task.setOnFailed(event -> {
            sqlTab.statusLabel.textProperty().unbind();
            sqlTab.statusLabel.setText("Export failed");
            sqlTab.downloadButton.setDisable(false);
            showAlert(AlertType.ERROR, "Error", "Export failed: " + task.getException().getMessage());
        });
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }
    
    /**
     * Utility method to show alerts
     */
    private void showAlert(AlertType alertType, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(alertType);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}