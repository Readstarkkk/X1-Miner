package com.x1miner.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BitcoinSha256dTest {
    @Test
    public void genesisHeaderMatchesKnownBlockHash() throws Exception {
        assertEquals(BitcoinSha256d.GENESIS_BLOCK_HASH,
                BitcoinSha256d.hashHeaderHex(BitcoinSha256d.GENESIS_HEADER_HEX));
        assertTrue(BitcoinSha256d.verifyGenesisVector());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHeaderThatIsNot80Bytes() throws Exception {
        BitcoinSha256d.hashHeaderHex("00");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonHexInput() throws Exception {
        BitcoinSha256d.hashHeaderHex("zz".repeat(80));
    }
}
