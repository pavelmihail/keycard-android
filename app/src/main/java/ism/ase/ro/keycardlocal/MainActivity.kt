package ism.ase.ro.keycardlocal

import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import ism.ase.ro.keycardlocal.util.android.NFCCardManager
import org.bouncycastle.util.encoders.Hex
import ism.ase.ro.keycardlocal.util.io.CardChannel
import ism.ase.ro.keycardlocal.util.io.CardListener
import ism.ase.ro.keycardlocal.util.applet.*
import ism.ase.ro.keycardlocal.util.applet.ApplicationInfo
import ism.ase.ro.keycardlocal.util.applet.KeycardCommandSet


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private var nfcAdapter: NfcAdapter? = null
    private var cardManager: NFCCardManager? = null
    //private LedgerBLEManager cardManager;
    //private boolean connected;

    //private LedgerBLEManager cardManager;
    //private boolean connected;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        cardManager = NFCCardManager()
        //cardManager = new LedgerBLEManager(this);
        cardManager!!.setCardListener(object : CardListener {
            override fun onConnected(cardChannel: CardChannel?) {
                try {
                    // Applet-specific code
                    val cmdSet = KeycardCommandSet(cardChannel)
                    Log.i(TAG, "Applet selection successful")

                    // First thing to do is selecting the applet on the card.
                    var info = ApplicationInfo(cmdSet.select().checkOK().getData())

                    // If the card is not initialized, the INIT apdu must be sent. The actual PIN, PUK and pairing password values
                    // can be either generated or chosen by the user. Using fixed values is highly discouraged.
                    if (!info.isInitializedCard()) {
                        Log.i(TAG, "Initializing card with test secrets")
                        cmdSet.init("000000", "123456789012", "KeycardTest").checkOK()
                        info = ApplicationInfo(cmdSet.select().checkOK().getData())
                    }
                    Log.i(TAG, "Instance UID: " + Hex.toHexString(info.getInstanceUID()))
                    Log.i(
                        TAG,
                        "Secure channel public key: " + Hex.toHexString(info.getSecureChannelPubKey())
                    )
                    Log.i(TAG, "Application version: " + info.getAppVersionString())
                    Log.i(TAG, "Free pairing slots: " + info.getFreePairingSlots())
                    if (info.hasMasterKey()) {
                        Log.i(TAG, "Key UID: " + Hex.toHexString(info.getKeyUID()))
                    } else {
                        Log.i(TAG, "The card has no master key")
                    }
                    Log.i(TAG, String.format("Capabilities: %02X", info.getCapabilities()))
                    Log.i(TAG, "Has Secure Channel: " + info.hasSecureChannelCapability())
                    Log.i(TAG, "Has Key Management: " + info.hasKeyManagementCapability())
                    Log.i(
                        TAG,
                        "Has Credentials Management: " + info.hasCredentialsManagementCapability()
                    )
                    Log.i(TAG, "Has NDEF capability: " + info.hasNDEFCapability())
                    if (info.hasSecureChannelCapability()) {
                        // In real projects, the pairing key should be saved and used for all new sessions.
                        cmdSet.autoPair("KeycardTest")
                        val pairing: Pairing = cmdSet.getPairing()

                        // Never log the pairing key in a real application!
                        Log.i(TAG, "Pairing with card is done.")
                        Log.i(TAG, "Pairing index: " + pairing.getPairingIndex())
                        Log.i(TAG, "Pairing key: " + Hex.toHexString(pairing.getPairingKey()))

                        // Opening a Secure Channel is needed for all other applet commands
                        cmdSet.autoOpenSecureChannel()
                        Log.i(TAG, "Secure channel opened. Getting applet status.")
                    }

                    // We send a GET STATUS command, which does not require PIN authentication
                    val status = ApplicationStatus(
                        cmdSet.getStatus(KeycardCommandSet.GET_STATUS_P1_APPLICATION).checkOK()
                            .getData()
                    )
                    Log.i(TAG, "PIN retry counter: " + status.getPINRetryCount())
                    Log.i(TAG, "PUK retry counter: " + status.getPUKRetryCount())
                    Log.i(TAG, "Has master key: " + status.hasMasterKey())
                    if (info.hasKeyManagementCapability()) {
                        // A mnemonic can be generated before PIN authentication. Generating a mnemonic does not create keys on the
                        // card. a subsequent loadKey step must be performed after PIN authentication. In this example we will only
                        // show how to convert the output of the card to a usable format but won't actually load the key
                        val mnemonic = Mnemonic(
                            cmdSet.generateMnemonic(KeycardCommandSet.GENERATE_MNEMONIC_12_WORDS)
                                .checkOK().getData()
                        )

                        // We need to set a wordlist if we plan using this object to derive the binary seed. If we just need the word
                        // indexes we can skip this step and call mnemonic.getIndexes() instead.
                        mnemonic.fetchBIP39EnglishWordlist()
                        Log.i(TAG, "Generated mnemonic phrase: " + mnemonic.toMnemonicPhrase())
                        Log.i(TAG, "Binary seed: " + Hex.toHexString(mnemonic.toBinarySeed()))
                    }
                    if (info.hasCredentialsManagementCapability()) {
                        // PIN authentication allows execution of privileged commands
                        cmdSet.verifyPIN("000000").checkAuthOK()
                        Log.i(TAG, "Pin Verified.")
                    }

                    // If the card has no keys, we generate a new set. Keys can also be loaded on the card starting from a binary
                    // seed generated from a mnemonic phrase. In alternative, we could load the generated keypair as shown in the
                    // commented line of code.
                    if (!status.hasMasterKey() && info.hasKeyManagementCapability()) {
                        cmdSet.generateKey()
                        //cmdSet.loadKey(mnemonic.toBIP32KeyPair());
                    }

                    // Get the current key path using GET STATUS
                    val currentPath = KeyPath(
                        cmdSet.getStatus(KeycardCommandSet.GET_STATUS_P1_KEY_PATH).checkOK()
                            .getData()
                    )
                    Log.i(TAG, "Current key path: $currentPath")
                    if (!currentPath.toString().equals("m/44'/0'/0'/0/0")) {
                        // Key derivation is needed to select the desired key. The derived key remains current until a new derive
                        // command is sent (it is not lost on power loss).
                        cmdSet.deriveKey("m/44'/0'/0'/0/0").checkOK()
                        Log.i(TAG, "Derived m/44'/0'/0'/0/0")
                    }

                    // We retrieve the wallet public key
                    val walletPublicKey: BIP32KeyPair =
                        BIP32KeyPair.fromTLV(cmdSet.exportCurrentKey(true).checkOK().getData())
                    Log.i(
                        TAG,
                        "Wallet public key: " + Hex.toHexString(walletPublicKey.getPublicKey())
                    )
                    Log.i(
                        TAG,
                        "Wallet address: " + Hex.toHexString(walletPublicKey.toEthereumAddress())
                    )
                    val hash = "thiscouldbeahashintheorysoitisok".toByteArray()
                    val signature =
                        RecoverableSignature(hash, cmdSet.sign(hash).checkOK().getData())
                    Log.i(TAG, "Signed hash: " + Hex.toHexString(hash))
                    Log.i(TAG, "Recovery ID: " + signature.getRecId())
                    Log.i(TAG, "R: " + Hex.toHexString(signature.getR()))
                    Log.i(TAG, "S: " + Hex.toHexString(signature.getS()))
                    if (info.hasSecureChannelCapability()) {
                        // Cleanup, in a real application you would not unpair and instead keep the pairing key for successive interactions.
                        // We also remove all other pairings so that we do not fill all slots with failing runs. Again in real application
                        // this would be a very bad idea to do.
                        cmdSet.unpairOthers()
                        cmdSet.autoUnpair()
                        Log.i(TAG, "Unpaired.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.message!!)
                }
            }

            override fun onDisconnected() {
                Log.i(TAG, "Card disconnected.")
            }
        })
        cardManager!!.start()
        /*connected = false;
    cardManager.startScan(new BluetoothAdapter.LeScanCallback() {
      @Override
      public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (connected) {
          return;
        }

        connected = true;
        cardManager.stopScan(this);
        cardManager.connectDevice(device);
      }
    });*/
    }

    override fun onResume() {
        super.onResume()
        if (nfcAdapter != null) {
            nfcAdapter!!.enableReaderMode(
                this,
                cardManager,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (nfcAdapter != null) {
            nfcAdapter!!.disableReaderMode(this)
        }
    }
}