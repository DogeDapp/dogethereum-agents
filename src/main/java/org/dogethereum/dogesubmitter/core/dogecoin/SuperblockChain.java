package org.dogethereum.dogesubmitter.core.dogecoin;

import org.apache.commons.lang3.time.DateUtils;
import org.bitcoinj.core.AltcoinBlock;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;

import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.crypto.Hash;

import javax.annotation.PostConstruct;
import java.io.File;
import java.math.BigInteger;
import java.util.*;

/**
 * Keeps a chain of superblocks in disk and provides information about it.
 * @author Catalina Juarros
 */

public class SuperblockChain {
    @Autowired
    private DogecoinWrapper dogecoinWrapper; // Interface with the Doge blockchain

    // Where the superblock chain is going to be stored in the disk
    private File dataDirectory;
    private File chainFile;

    private int bestSuperblockHeight;

    /**
     * Class constructor
     * @param dogecoinWrapper Dogecoin blockchain interface
     */
    public SuperblockChain(DogecoinWrapper dogecoinWrapper) {
        this.dogecoinWrapper = dogecoinWrapper;
    }

    /**
     * Gets necessary blocks for building a superblock starting at a given time
     * @param initialDate Timestamp of the first block in the superblock
     * @param initialHeight Height of the first block in the superblock
     * @param blocks List to add the blocks to
     * @return Height of the earliest Doge block that was not added to the list
     * @throws BlockStoreException
     */
    private int fillWithBlocksStartingAtTime(Date initialDate, int initialHeight, List<AltcoinBlock> blocks) throws BlockStoreException {
        int bestChainHeight = dogecoinWrapper.getBestChainHeight();
        int currentHeight = initialHeight;
        AltcoinBlock currentBlock = (AltcoinBlock) dogecoinWrapper.getStoredBlockAtHeight(initialHeight).getHeader();
        Date currentDate = currentBlock.getTime();

        // While the current block was *not* mined an hour or more after the initial date,
        // keep adding blocks to the array.
        // This is equivalent to saying the current block's truncated date
        // is less than or equal to the initial date.
        // It's allowed to be less than the initial date
        // because a Dogecoin timestamp can be less than that of a previous block
        // and still be valid.
        while (DateUtils.truncatedCompareTo(initialDate, currentDate, Calendar.HOUR) >= 0 && currentHeight < bestChainHeight) {
            blocks.add(currentBlock);
            currentHeight++; // loop condition ensures that this height will always be valid
            currentBlock = (AltcoinBlock) dogecoinWrapper.getStoredBlockAtHeight(currentHeight).getHeader();
            currentDate = currentBlock.getTime();
        }

        return currentHeight;
    }

    @PostConstruct
    /**
     * Builds a chain of superblocks from the whole Dogecoin blockchain.
     * Right now this is only for testing. It will be useful when it writes to disk.
     * @throws BlockStoreException
     * @throws java.io.IOException
     */
    public void buildChainFromGenesis() throws BlockStoreException, java.io.IOException {
        List<Superblock> superblocks = new ArrayList<>(); // entire chain of superblocks
        List<AltcoinBlock> currentBlocksToHash = new ArrayList<>(); // Dogecoin blocks for the next superblock

        int bestChainHeight = dogecoinWrapper.getBestChainHeight();
        int currentHeight = 0;

        StoredBlock currentStoredBlock = dogecoinWrapper.getStoredBlockAtHeight(currentHeight); // start with genesis block
        BigInteger currentWork = currentStoredBlock.getChainWork(); // chain work for the block that will be built from currentBlocksToHash
        AltcoinBlock currentBlock = (AltcoinBlock) currentStoredBlock.getHeader(); // first block of the next superblock
        Date currentInitialDate = currentBlock.getTime(); // timestamp of the first block of the next superblock

        byte[] previousSuperblockHash = Hash.sha3("0000000000000000000000000000000000000000000000000000000000000000".getBytes());

        while (currentHeight < bestChainHeight) {
            // builds list and advances height
            currentHeight = fillWithBlocksStartingAtTime(currentInitialDate, currentHeight, currentBlocksToHash);
            Superblock newSuperblock = new Superblock(currentBlocksToHash, previousSuperblockHash, currentWork, currentHeight);
            superblocks.add(newSuperblock);

            // update necessary fields for next superblock
            if (currentHeight <= bestChainHeight) {
                currentStoredBlock = dogecoinWrapper.getStoredBlockAtHeight(currentHeight);
                currentWork = currentStoredBlock.getChainWork();
                currentBlock = (AltcoinBlock) currentStoredBlock.getHeader();
                currentInitialDate = currentBlock.getTime();
                previousSuperblockHash = newSuperblock.getHash();
            }
        }
    }

//    public void buildChainFromHeight(int initialHeight) throws BlockStoreException, java.io.IOException {
//        int bestChainHeight = dogecoinWrapper.getBestChainHeight();
//        StoredBlock currentStoredBlock = dogecoinWrapper.getBlockAtHeight(initialHeight);
//        AltcoinBlock currentBlock = (AltcoinBlock) currentStoredBlock.getHeader();
//        long firstBlockOfSuperblockTime = currentStoredBlock.getHeader().getTimeSeconds(); // timestamp of the first block in the superblock being built
//        byte[] previousSuperblockHash = new byte[1]; // dummy value for now
//        BigInteger currentInitialWork = currentStoredBlock.getChainWork(); // chain work of the first block in the superblock being built
//
//        // list of blocks that a superblock will be composed of
//        // this must be cleared after the superblock is built
//        List<AltcoinBlock> blocksToHash = new ArrayList<>();
//        blocksToHash.add(currentBlock);
//        // I'll probably do without this when I figure out how to write a superblock to disk
//        List<Superblock> superblocks = new ArrayList<>();
//
//        for (int i = initialHeight + 1; i < bestChainHeight; i++) {
//            currentStoredBlock = dogecoinWrapper.getBlockAtHeight(i);
//            currentBlock = (AltcoinBlock) currentStoredBlock.getHeader();
//
//            if (currentBlock.getTimeSeconds() - firstBlockOfSuperblockTime > 3600) {
//                // blocksToHash already contains blocks mined within an hour,
//                // so they must be hashed into a superblock and the list must be cleared
//                Superblock newSuperblock = new Superblock(blocksToHash, previousSuperblockHash, currentInitialWork);
//                superblocks.add(newSuperblock);
//                previousSuperblockHash = newSuperblock.getHash(); // TODO: maybe I need to hash the whole data - ask later
//
//                // restart list and set currentBlock as the first block of the next superblock
//                blocksToHash = new ArrayList<>();
//                blocksToHash.add(currentBlock);
//                currentInitialWork = currentStoredBlock.getChainWork();
//            } else {
//                blocksToHash.add(currentBlock);
//            }
//        }
//    }
}