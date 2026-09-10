package org.nitish.project.sharedrop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds


// ------------------------------------------------------------
// APP ENTRY COMPOSABLE
// ------------------------------------------------------------
//
// Root of the Compose UI.
//
// Android MainActivity and JVM MainKt can both call:
//
//     App()
//
// Because this is inside commonMain, the same UI is shared
// between platforms.
//
@Composable
fun App() {
    MaterialTheme {
        HomeScreen()
    }
}


// ------------------------------------------------------------
// HOME SCREEN
// ------------------------------------------------------------
//
// Main screen of ShareDrop.
//
// Responsible for:
//
// 1. Discovering nearby devices
// 2. Advertising this device
// 3. Sending files
// 4. Receiving files
// 5. Displaying transfer progress
// 6. Displaying received files
//
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {

    // --------------------------------------------------------
    // UI STATE
    // --------------------------------------------------------

    // Devices currently visible on the network.
    val nearbyDevices = remember {
        mutableStateListOf<DiscoveredDevice>()
    }

    // Stores the last time each device was seen.
    //
    // Key   = device IP address
    // Value = timestamp
    val lastSeenMap = remember {
        mutableMapOf<String, Long>()
    }

    // Currently selected device.
    //
    // null = no device selected.
    var selectedDevice by remember {
        mutableStateOf<DiscoveredDevice?>(null)
    }

    // Message displayed to the user.
    var statusMessage by remember {
        mutableStateOf("")
    }

    // Transferred MB
    var transferredMB by remember { mutableStateOf(0L) }

    // Total MB
    var totalMB by remember { mutableStateOf(0L) }

    // Transfer Speed
    var transferSpeed by remember {
        mutableStateOf(0.0)
    }
    val receiveSpeedTracker = remember { TransferSpeedTracker() }
    val sendSpeedTracker = remember { TransferSpeedTracker() }


    // Transfer progress.
    //
    // 0f   = 0%
    // 1f   = 100%
    var transferProgress by remember {
        mutableStateOf(0f)
    }

    // True while THIS device is sending a file.
    var isSending by remember {
        mutableStateOf(false)
    }

    // True while Android copies a selected document into a local transfer file.
    var isPreparingFile by remember {
        mutableStateOf(false)
    }

    // True while THIS device is receiving a file.
    var isReceiving by remember {
        mutableStateOf(false)
    }

    // Files successfully received during this session.
    val receivedFiles = remember {
        mutableStateListOf<String>()
    }


    // --------------------------------------------------------
    // NETWORK / FILE COMPONENTS
    // --------------------------------------------------------

    // Discovers nearby ShareDrop devices.
    val discovery = remember {
        DeviceDiscovery()
    }

    // Advertises this device to other ShareDrop devices.
    val advertiser = remember {
        DeviceAdvertiser()
    }

    // Handles incoming files.
    val receiver = remember {
        FileReceiver()
    }

    // Handles outgoing files.
    //
    // IMPORTANT:
    // We keep ONE instance using remember.
    //
    // This is important because cancel() must be called
    // on the SAME FileSender instance that started sendFile().
    val sender = remember {
        FileSender()
    }

    // File picker.
    val filePicker = remember {
        FilePicker()
    }

    // Coroutine scope associated with this screen.
    val scope = rememberCoroutineScope()


    // --------------------------------------------------------
    // SETTINGS
    // --------------------------------------------------------

    val settings = remember {
        Settings(
            deviceNameStorage = provideDeviceNameStorage()
        )
    }

    val appSettings by settings.settingsFlow.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )

    val localDeviceName = appSettings.localName


    // --------------------------------------------------------
    // LIFECYCLE
    // --------------------------------------------------------

    val lifecycleOwner = LocalLifecycleOwner.current

    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()


    // --------------------------------------------------------
    // LOAD SETTINGS
    // --------------------------------------------------------

    LaunchedEffect(settings) {
        settings.ensureLoaded()
    }


    // --------------------------------------------------------
    // START NETWORK SERVICES
    // --------------------------------------------------------

    LaunchedEffect(
        localDeviceName, lifecycleState
    ) {

        // Wait until the device name has loaded.
        if (localDeviceName == null) {
            return@LaunchedEffect
        }

        // Only run networking while the screen is active.
        if (lifecycleState != Lifecycle.State.RESUMED) {
            return@LaunchedEffect
        }


        // ----------------------------------------------------
        // ADVERTISE THIS DEVICE
        // ----------------------------------------------------

        advertiser.startAdvertising(
            localDeviceName, 8080
        )


        // ----------------------------------------------------
        // DISCOVER OTHER DEVICES
        // ----------------------------------------------------

        discovery.startDiscovery { device ->

            // Discovery callback may happen on a background
            // thread, so switch to Main before modifying
            // Compose state.
            scope.launch(Dispatchers.Main) {

                val currentTime = Clock.System.now().toEpochMilliseconds()

                lastSeenMap[device.host] = currentTime


                // Check whether this device already exists.
                val existingIndex = nearbyDevices.indexOfFirst {
                    it.host == device.host
                }


                // ------------------------------------------------
                // NEW DEVICE
                // ------------------------------------------------

                if (existingIndex == -1) {

                    // Do not display our own device.
                    //
                    // NOTE:
                    // If two devices have the exact same name,
                    // this check can cause problems.
                    //
                    // A better solution later would be comparing
                    // a unique device ID instead of device name.
                    if (device.name != localDeviceName) {
                        nearbyDevices.add(device)
                    }

                }

                // ------------------------------------------------
                // EXISTING DEVICE
                // ------------------------------------------------

                else if (nearbyDevices[existingIndex].name != device.name) {
                    nearbyDevices[existingIndex] = device
                }
            }
        }


        // ----------------------------------------------------
        // REMOVE DISCONNECTED DEVICES
        // ----------------------------------------------------

        launch {

            while (isActive) {

                // Check every 2 seconds.
                delay(2000.milliseconds)

                val currentTime = Clock.System.now().toEpochMilliseconds()

                // Device is considered gone after 10 seconds
                // without being discovered again.
                val timeout = 10_000L

                val expiredHosts = lastSeenMap.filter { (_, lastSeen) ->
                    currentTime - lastSeen > timeout
                }.keys


                if (expiredHosts.isNotEmpty()) {

                    withContext(Dispatchers.Main) {

                        expiredHosts.forEach { host ->

                            nearbyDevices.removeAll {
                                it.host == host
                            }

                            lastSeenMap.remove(host)
                        }
                    }
                }
            }
        }


        // ----------------------------------------------------
        // START FILE RECEIVER
        // ----------------------------------------------------

        receiver.startReceiving(
            port = 8080,

            onProgress = { fileName, progress, received, total ->

                if (!isReceiving) {
                    receiveSpeedTracker.reset()
                }


                // Receiving has started.
                isReceiving = true

                statusMessage = "Receiving '$fileName'..."

                receiveSpeedTracker.update(received)?.let { transferSpeed = it }

                transferProgress = progress

                transferredMB = received / 1024 / 1024

                totalMB = total / 1024 / 1024
            },

            onFileReceived = { fileName, tempFilePath ->

                // The receiving socket has successfully
                // received the complete file.
                isReceiving = false

                transferProgress = 0f

                // Move the temporary file to final storage.
                FileSaver().moveFile(
                    fileName = fileName, sourcePath = tempFilePath
                ) { success, path ->

                    // Add file to received list.
                    receivedFiles.add(fileName)

                    statusMessage = if (success) {
                        "Saved: $fileName to $path"
                    } else {
                        "Received but failed to save: $fileName"
                    }
                }
            })
    }


    // --------------------------------------------------------
    // CLEANUP
    // --------------------------------------------------------

    DisposableEffect(Unit) {

        onDispose {

            // Stop advertising this device.
            advertiser.stopAdvertising()

            // Stop looking for other devices.
            discovery.stopDiscovery()

            // Stop receiving files.
            receiver.stopReceiving()

            // Cancel any outgoing transfer.
            sender.cancel()
        }
    }


    // --------------------------------------------------------
    // MAIN UI
    // --------------------------------------------------------

    Scaffold(

        // ----------------------------------------------------
        // TOP BAR
        // ----------------------------------------------------

        topBar = {

            TopAppBar(
                title = {
                    Text("ShareDrop")
                })
        },


        // ----------------------------------------------------
        // BOTTOM BAR
        // ----------------------------------------------------

        bottomBar = {

            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),

                horizontalAlignment = Alignment.CenterHorizontally
            ) {


                // ------------------------------------------------
                // STATUS MESSAGE
                // ------------------------------------------------

                if (statusMessage.isNotEmpty()) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (totalMB > 1024) {
                                "${transferredMB / 1024} Gb / ${totalMB / 1024} Gb"
                            } else {
                                "$transferredMB Mb / $totalMB Mb"
                            }
                        )

                        Text(
                            text = "${(transferSpeed / 1024.0 / 1024.0).toInt()} MB/s"
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),

                        horizontalArrangement = Arrangement.SpaceBetween,

                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = statusMessage,

                            style = MaterialTheme.typography.bodySmall,

                            modifier = Modifier.weight(1f)
                        )


                        // Display percentage while transferring.
                        if (transferProgress > 0f && transferProgress < 1f) {

                            Text(
                                text = "${(transferProgress * 100).toInt()}%",

                                style = MaterialTheme.typography.labelLarge,

                                color = MaterialTheme.colorScheme.primary,

                                fontWeight = FontWeight.Bold
                            )
                        }
                    }


                    // ------------------------------------------------
                    // PROGRESS BAR
                    // ------------------------------------------------

                    if (isPreparingFile || (transferProgress > 0f && transferProgress < 1f)) {

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        if (isPreparingFile) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = {
                                    transferProgress
                                },

                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }


                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )
                }


                // ------------------------------------------------
                // SEND / CANCEL BUTTON
                // ------------------------------------------------

                Button(

                    // The button must remain enabled while a
                    // transfer is running so that the user can
                    // press Cancel.
                    enabled = !isPreparingFile && (selectedDevice != null || isSending || isReceiving),


                    // ------------------------------------------------
                    // BUTTON CLICK
                    // ------------------------------------------------

                    onClick = {

                        // --------------------------------------------
                        // CANCEL SENDING
                        // --------------------------------------------

                        if (isSending) {

                            // Cancel the SAME sender instance
                            // that started the transfer.
                            sender.cancel()

                            isSending = false
                            transferProgress = 0f
                            statusMessage = "Transfer canceled"

                            return@Button
                        }


                        // --------------------------------------------
                        // CANCEL RECEIVING
                        // --------------------------------------------

                        if (isReceiving) {

                            // Close the active sender connection but keep listening
                            // for future transfers.
                            receiver.cancelCurrentTransfer()

                            isReceiving = false
                            transferProgress = 0f
                            statusMessage = "Transfer canceled"

                            return@Button
                        }


                        // --------------------------------------------
                        // SEND FILE
                        // --------------------------------------------

                        val device = selectedDevice

                        if (device == null) {

                            statusMessage = "Please select a device first!"

                            return@Button
                        }


                        // Open platform-specific file picker.
                        filePicker.pickFile(onFilePicked = { absolutePath ->

                            val fileName = absolutePath.pathToFileName()

                            isPreparingFile = false

                            statusMessage = "Sending '$fileName'..."


                            // Mark transfer as active.
                            isSending = true

                            transferProgress = 0.01f


                            // IMPORTANT:
                            // Use the remembered sender instance.
                            //
                            // DO NOT write:
                            //
                            //     FileSender().sendFile(...)
                            //
                            // because then cancel() would be
                            // called on a different FileSender.

                            sendSpeedTracker.reset()
                            sender.sendFile(

                                host = device.host,

                                port = device.port,

                                absolutePath = absolutePath,


                                // --------------------------------
                                // PROGRESS CALLBACK
                                // --------------------------------

                                onProgress = { progress, transferred, total ->

                                    scope.launch(
                                        Dispatchers.Main
                                    ) {

                                        sendSpeedTracker.update(transferred)
                                            ?.let { transferSpeed = it }
                                        transferProgress = progress
                                        transferredMB = (transferred / 1024 / 1024)
                                        totalMB = (total / 1024 / 1024)
                                    }
                                },


                                // --------------------------------
                                // RESULT CALLBACK
                                // --------------------------------

                                onResult = { success ->

                                    scope.launch(
                                        Dispatchers.Main
                                    ) {

                                        // Transfer is no longer
                                        // running.
                                        isSending = false

                                        transferProgress = 0f


                                        // Display result.
                                        statusMessage = if (success) {

                                            "Sent '$fileName'!"

                                        } else {

                                            "Transfer failed"
                                        }
                                    }
                                })
                        }, onFilePreparationStarted = {
                            isPreparingFile = true
                            statusMessage = "Preparing selected file..."
                        }, onFilePickFailed = {
                            isPreparingFile = false
                            statusMessage = "Unable to prepare selected file"
                        })
                    }) {

                    // ------------------------------------------------
                    // BUTTON TEXT
                    // ------------------------------------------------

                    Text(

                        when {

                            isPreparingFile -> "Preparing file..."

                            isSending -> "Cancel"

                            isReceiving -> "Cancel"

                            selectedDevice == null -> "Select a device to send"

                            else -> "Send File to ${selectedDevice!!.name}"
                        }
                    )
                }
            }
        }

    ) { innerPadding ->


        // --------------------------------------------------------
        // MAIN CONTENT
        // --------------------------------------------------------

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)
        ) {


            // ----------------------------------------------------
            // LOCAL DEVICE NAME
            // ----------------------------------------------------

            Text(
                text = "Your device name: ${
                    localDeviceName ?: "Loading..."
                }",

                style = MaterialTheme.typography.bodyMedium
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            HorizontalDivider(
                modifier = Modifier.fillMaxWidth()
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            // ----------------------------------------------------
            // NEARBY DEVICES
            // ----------------------------------------------------

            Text(
                text = "Nearby Devices",

                style = MaterialTheme.typography.titleMedium
            )


            Spacer(
                modifier = Modifier.height(8.dp)
            )


            if (nearbyDevices.isEmpty()) {

                // No devices discovered yet.
                Box(
                    modifier = Modifier.fillMaxSize(),

                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "Searching for devices...",

                        style = MaterialTheme.typography.bodyMedium,

                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            } else {

                // ------------------------------------------------
                // DEVICE LIST
                // ------------------------------------------------

                LazyColumn {

                    items(
                        items = nearbyDevices
                    ) { device ->

                        Card(

                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {

                                // Select/deselect device.
                                selectedDevice = if (selectedDevice == device) {
                                    null
                                } else {
                                    device
                                }
                            },


                            // Highlight selected device.
                            colors = CardDefaults.cardColors(

                                containerColor = if (selectedDevice == device) {

                                    MaterialTheme.colorScheme.primaryContainer

                                } else {

                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        ) {

                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {

                                // Device name.
                                Text(
                                    text = device.name,

                                    style = MaterialTheme.typography.bodyLarge
                                )


                                // Device address.
                                Text(
                                    text = "${device.host}:${device.port}",

                                    style = MaterialTheme.typography.bodySmall,

                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }


            // ----------------------------------------------------
            // RECEIVED FILES
            // ----------------------------------------------------

            if (receivedFiles.isNotEmpty()) {

                Spacer(
                    modifier = Modifier.height(16.dp)
                )


                Text(
                    text = "Received Files",

                    style = MaterialTheme.typography.titleMedium
                )


                Spacer(
                    modifier = Modifier.height(8.dp)
                )


                LazyColumn {

                    items(
                        items = receivedFiles
                    ) { fileName ->

                        Text(
                            text = fileName,

                            modifier = Modifier.padding(
                                vertical = 4.dp
                            )
                        )
                    }
                }
            }
        }
    }
}


// ------------------------------------------------------------
// FILE PATH HELPER
// ------------------------------------------------------------
//
// Converts a complete file path into just the filename.
//
// Example:
//
// /Users/nitish/Downloads/photo.jpg
//
// becomes:
//
// photo.jpg
//
// Also handles Windows paths:
//
// C:\Users\Nitish\Downloads\photo.jpg
//
// becomes:
//
// photo.jpg
//
private fun String.pathToFileName(): String {

    return replace("\\", "/").substringAfterLast('/')
}
