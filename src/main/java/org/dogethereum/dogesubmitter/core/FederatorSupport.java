package org.dogethereum.dogesubmitter.core;


import lombok.extern.slf4j.Slf4j;
import org.dogethereum.dogesubmitter.constants.SystemProperties;
import org.dogethereum.dogesubmitter.contract.DogeRelay;
import org.dogethereum.dogesubmitter.contract.DogeToken;
import org.bitcoinj.core.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.libdohj.core.ScryptHash;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Helps the federator communication with the Eth blockchain.
 * @author Oscar Guindzberg
 */
@Component
@Slf4j(topic = "FederatorSupport")
public class FederatorSupport {

    //private Ethereum ethereum;

    //private FederateKeyHandler keyHandler;

    //private AccountBuilder accountBuilder;


    //private Blockchain blockchain;

    //PendingState pendingState;

    //BigInteger gasPrice;

    //private FedNodeSystemProperties config;

    //private org.bitcoinj.core.ECKey publicKey;

    //private NetworkParameters parameters;

    //private String bridgeContractAddress;

    private Web3j web3;
    private DogeRelay dogeRelay;
    private DogeRelay dogeRelayForRelayTx;
    private DogeToken dogeToken;
    private SystemProperties config;
    private BigInteger gasPriceMinimum;
    private String truebitClaimantAddress;

    @Autowired
    public FederatorSupport() throws Exception {
        config = SystemProperties.CONFIG;
        web3 = Web3j.build(new HttpService());  // defaults to http://localhost:8545/
        String dogeRelayContractAddress;
        String dogeTokenContractAddress;
        String fromAddressGeneralPurposeAndSendBlocks;
        String fromAddressRelayTxs;
        if (config.isRegtest()) {
            dogeRelayContractAddress = getContractAddress("DogeRelay");
            dogeTokenContractAddress = getContractAddress("DogeToken");
            fromAddressGeneralPurposeAndSendBlocks = web3.ethAccounts().send().getAccounts().get(0);
            fromAddressRelayTxs = web3.ethAccounts().send().getAccounts().get(1);
            truebitClaimantAddress = web3.ethAccounts().send().getAccounts().get(2);
        } else {
            dogeRelayContractAddress = config.dogeRelayContractAddress();
            dogeTokenContractAddress = config.dogeTokenContractAddress();
            fromAddressGeneralPurposeAndSendBlocks = config.addressGeneralPurposeAndSendBlocks();
            fromAddressRelayTxs = config.addressRelayTxs();
            truebitClaimantAddress = config.truebitClaimantAddress();
        }
        gasPriceMinimum = BigInteger.valueOf(config.gasPriceMinimum());
        BigInteger gasLimit = BigInteger.valueOf(config.gasLimit());
        dogeRelay = DogeRelay.load(dogeRelayContractAddress, web3, new ClientTransactionManager(web3, fromAddressGeneralPurposeAndSendBlocks), gasPriceMinimum, gasLimit);
        assert dogeRelay.isValid();
        dogeRelayForRelayTx = DogeRelay.load(dogeRelayContractAddress, web3, new ClientTransactionManager(web3, fromAddressRelayTxs), gasPriceMinimum, gasLimit);
        assert dogeRelayForRelayTx.isValid();
        dogeToken = DogeToken.load(dogeTokenContractAddress, web3, new ClientTransactionManager(web3, fromAddressGeneralPurposeAndSendBlocks), gasPriceMinimum, gasLimit);
        assert dogeToken.isValid();
    }

//    @Autowired
//    public FederatorSupport(Ethereum ethereum, AccountBuilder accountBuilder, Blockchain blockchain, PendingState pendingState, FedNodeSystemProperties config) {
//        this.ethereum = ethereum;
//        this.accountBuilder = accountBuilder;
//        this.blockchain = blockchain;
//        this.pendingState = pendingState;
//        this.config = config;
//        this.keyHandler = new FederateKeyHandler(this.config.federatorKeyFile());
//        this.gasPrice = BigInteger.valueOf(config.federatorGasPrice());
//        this.bridgeConstants = config.getBridgeConstants();
//        this.parameters = this.bridgeConstants.getDogeParams();
//        this.bridgeContractAddress = this.bridgeConstants.getBridgeContractAddress();
//    }

//    public Object callTx(CallTransaction.Function function) {
//        ProgramResult res = ethereum.callConstantFunction(bridgeContractAddress, function);
//        Object[] result = function.decodeResult(res.getHReturn());
//        return result[0];
//    }


//    public byte[] getFederatorPrivKeyBytes() {
//        return keyHandler.privateKey();
//    }

//    public byte[] getFederatorPubKeyBytes() {
//        return this.getPubKey().getPubKey();
//    }

//    public org.bitcoinj.core.ECKey getPubKey() {
//        if (this.publicKey == null) {
//            this.publicKey = org.bitcoinj.core.ECKey.fromPrivate(this.getFederatorPrivKeyBytes());
//        }
//        return this.publicKey;
//    }

//    public List<PeerAddress> getDogecoinPeerAddresses() throws UnknownHostException {
//        return DogecoinPeerFactory.buildDogecoinPeerAddresses(this.bridgeConstants.getDogeParams().getPort(), this.config.dogecoinPeerAddresses());
//    }

    /**
     * Get the deployed contract address from a truffle json file
     * @param contractName
     * @return The contract address
     * @throws Exception
     */
    private String getContractAddress(String contractName) throws Exception {
        String basePath = config.truffleBuildContractsDirectory();
        FileReader dogeRelaySpecFile = new FileReader(basePath + "/" + contractName + ".json");
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(dogeRelaySpecFile);
        JSONObject jsonObject =  (JSONObject) obj;
        JSONObject jsonObject2 =  (JSONObject) jsonObject.get("networks");
        JSONObject jsonObject3 =  (JSONObject) jsonObject2.values().iterator().next();
        String value4 = (String) jsonObject3.get("address");
        return value4;
    }

    public int getDogeBestBlockHeight() throws Exception {
        return dogeRelay.getBestBlockHeight().send().intValue();
    }

    public String getDogeBestBlockHash() throws Exception {
        BigInteger result = dogeRelay.getBestBlockHash().send();
        return hashBigIntegerToString(result);
    }

    private String hashBigIntegerToString(BigInteger input) {
        String input2 = input.toString(16);
        StringBuilder output = new StringBuilder(input2);
        while (output.length()<64) {
            output.insert(0,"0");
        }
        return output.toString();
    }


    public List<String> getDogeBlockchainBlockLocator() throws Exception {
        List<Uint256> result = dogeRelay.getBlockLocator().send();
        List<String> formattedResult = new ArrayList<>();
        for (Uint256 uint256Hash : result) {
            formattedResult.add(hashBigIntegerToString(uint256Hash.getValue()));
        }
        return formattedResult;
    }

    public void sendStoreHeaders(org.bitcoinj.core.Block headers[]) throws Exception {
        log.info("About to send to the bridge headers from {} to {}", headers[0].getHash(), headers[headers.length - 1].getHash());
//        ByteArrayOutputStream baosHeaders = new ByteArrayOutputStream();
//        ByteArrayOutputStream baosHashes = new ByteArrayOutputStream();
//        for (int i = 0; i < headers.length; i++) {
//            byte[] serializedHeader = headers[i].bitcoinSerialize();
//            byte[] headerSize = calculateHeaderSize(serializedHeader);
//            baosHeaders.write(headerSize);
//            baosHeaders.write(serializedHeader);
//            NetworkParameters params = config.getBridgeConstants().getDogeParams();
//            AltcoinBlock block = new AltcoinBlock(params, serializedHeader);
//            if (block.getAuxPoW() == null) {
//                baosHashes.write(block.getScryptHash().getBytes());
//            } else {
//                baosHashes.write(block.getAuxPoW().getParentBlockHeader().getScryptHash().getBytes());
//            }
//        }
//
//        CompletableFuture<TransactionReceipt> futureReceipt = dogeRelay.bulkStoreHeaders(baosHeaders.toByteArray(), baosHashes.toByteArray(), BigInteger.valueOf(headers.length)).sendAsync();
//        futureReceipt.thenAcceptAsync( (TransactionReceipt receipt) -> log.info("BulkStoreHeaders receipt {}.", toString(receipt)) );
        for (Block header : headers) {
            AltcoinBlock dogeHeader = (AltcoinBlock) header;
            ScryptHash scryptHash;
            if (dogeHeader.getAuxPoW() == null) {
                scryptHash = dogeHeader.getScryptHash();
            } else {
                scryptHash = dogeHeader.getAuxPoW().getParentBlockHeader().getScryptHash();
            }
            BigInteger scryptHashBI = ScryptHash.wrapReversed(scryptHash.getBytes()).toBigInteger();
            CompletableFuture<TransactionReceipt> futureReceipt = dogeRelay.storeBlockHeader(dogeHeader.bitcoinSerialize(), scryptHashBI, truebitClaimantAddress).sendAsync();
            log.info("Sent storeBlockHeader {}", dogeHeader.getHash());
            futureReceipt.thenAcceptAsync( (TransactionReceipt receipt) ->
                    log.info("StoreBlockHeader receipt {}.", toString(receipt))
            );
            // Sleep a couple of millis, executing on ganache I get some tx receipt with "no previous block" error
            Thread.sleep(200);
        }
    }

    // Return the size of the header as a 4-byte byte[]
    private byte[] calculateHeaderSize (byte[] header) {
        String size = BigInteger.valueOf(header.length).toString(16);
        while (size.length() < 8) {
            size = "0" + size;
        }
        return Hex.decode(size);
    }

    public boolean wasLockTxProcessed(Sha256Hash txHash) throws Exception {
        return dogeToken.wasLockTxProcessed(txHash.toBigInteger()).send();

    }

    public void sendRelayTx(org.bitcoinj.core.Transaction tx, Sha256Hash blockHash, PartialMerkleTree pmt) throws Exception {
        log.info("About to send to the bridge doge tx hash {}. Block hash {}", tx.getHash(), blockHash);

        byte[] txSerialized = tx.bitcoinSerialize();

        BigInteger txIndex = BigInteger.valueOf(pmt.getTransactionIndex(tx.getHash()));
        List<Sha256Hash> siblingsSha256Hash = pmt.getTransactionPath(tx.getHash());
        List<BigInteger> siblingsBigInteger = new ArrayList<>();
        for (Sha256Hash sha256Hash : siblingsSha256Hash) {
            siblingsBigInteger.add(sha256Hash.toBigInteger());
        }
        BigInteger blockHashBigInteger = blockHash.toBigInteger();
        String targetContract = dogeToken.getContractAddress();
        CompletableFuture<TransactionReceipt> futureReceipt = dogeRelayForRelayTx.relayTx(txSerialized, txIndex, siblingsBigInteger, blockHashBigInteger, targetContract).sendAsync();
        log.info("Sent relayTx {}", tx.getHash());
        futureReceipt.thenAcceptAsync( (TransactionReceipt receipt) ->
                log.info("RelayTx receipt {}.", toString(receipt))
        );
    }

    private String toString(TransactionReceipt receipt) {
        return receipt.toString();
//        StringBuilder sb = new StringBuilder();
//        sb.append("Logs[")
//        for (Log receiptLog : receipt.getLogs()) {
//            sb.append(" Log " + receiptLog.getData());
//            for (String topic : receiptLog.getTopics()) {
//                sb.append(" Topic " + receiptLog.getData());
//            }
//            sb.append(". ");
//        }
//        sb.append("], ");
//        sb.append(receipt.getBlockHash());
//        return sb.toString();
    }

    public boolean isEthNodeSyncing() throws IOException {
        return web3.ethSyncing().send().isSyncing();
    }

    public void updateContractFacadesGasPrice() throws IOException {
        BigInteger gasPriceSuggestedByEthNode = web3.ethGasPrice().send().getGasPrice();
        BigInteger gasPrice;
        if (gasPriceSuggestedByEthNode.compareTo(gasPriceMinimum) > 0) {
            gasPrice = gasPriceSuggestedByEthNode;
        } else {
            gasPrice = gasPriceMinimum;
        }
        dogeRelay.setGasPrice(gasPrice);
        dogeToken.setGasPrice(gasPrice);
    }



//    Used just for release process
//    public StateForFederator getStateForFederator() throws IOException, ClassNotFoundException {
//        byte[] result = (byte[]) this.callTx(Bridge.GET_STATE_FOR_DOGE_RELEASE_CLIENT);
//        return new StateForFederator(result, this.parameters);
//    }
//
//    public void addSignature(List<byte[]> signatures, byte[] ethTxHash) {
//        byte[] federatorPublicKeyBytes = this.getFederatorPubKeyBytes();
//        this.sendEthTx(Bridge.ADD_SIGNATURE, federatorPublicKeyBytes, signatures, ethTxHash);
//    }
//
//    public void sendUpdateCollections() {
//        this.sendEthTx(Bridge.UPDATE_COLLECTIONS);
//    }
}
