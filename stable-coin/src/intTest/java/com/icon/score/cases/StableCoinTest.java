/*
 * Copyright 2020 ICONLOOP Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.icon.score.cases;

import foundation.icon.icx.IconService;
import foundation.icon.icx.KeyWallet;
import foundation.icon.icx.data.Address;
import foundation.icon.icx.data.Bytes;
import foundation.icon.icx.transport.http.HttpProvider;
import foundation.icon.icx.transport.jsonrpc.RpcError;
import foundation.icon.test.Env;
import foundation.icon.test.ResultTimeoutException;
import foundation.icon.test.TestBase;
import foundation.icon.test.TransactionFailureException;
import foundation.icon.test.TransactionHandler;
import com.icon.score.score.StableCoinScore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;

import static foundation.icon.test.Env.LOG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class StableCoinTest extends TestBase {
    private static final boolean DEBUG = true;
    private static final Address ZERO_ADDRESS = new Address("hx0000000000000000000000000000000000000000");
    private static TransactionHandler txHandler;
    private static SecureRandom secureRandom;
    private static KeyWallet[] wallets;
    private static KeyWallet ownerWallet, caller;

    @BeforeEach
     void setup() throws Exception {
        Env.Chain chain = Env.getDefaultChain();
        IconService iconService = new IconService(new HttpProvider(chain.getEndpointURL(3)));
        txHandler = new TransactionHandler(iconService, chain);
        secureRandom = new SecureRandom();

        // init wallets
        wallets = new KeyWallet[2];
        BigInteger amount = ICX.multiply(BigInteger.valueOf(50));
        for (int i = 0; i < wallets.length; i++) {
            wallets[i] = KeyWallet.create();
            txHandler.transfer(wallets[i].getAddress(), amount);
        }
        for (KeyWallet wallet : wallets) {
            ensureIcxBalance(txHandler, wallet.getAddress(), BigInteger.ZERO, amount);
        }
        ownerWallet = wallets[0];
        caller = wallets[1];

    }

    @AfterAll
    static void shutdown() throws Exception {
        for (KeyWallet wallet : wallets) {
            txHandler.refundAll(wallet);
        }
    }

    @Test
    public void testStableTokenContractFlow() throws Exception {

        StableCoinScore tokenScore = StableCoinScore.mustDeploy(txHandler, ownerWallet);
        // 1. initial check - getters
        LOG.infoEntering("initial check");
        assertEquals(BigInteger.ZERO, tokenScore.balanceOf(ownerWallet.getAddress()));
        assertEquals(BigInteger.ZERO, tokenScore.balanceOf(caller.getAddress()));
        assertEquals(BigInteger.ZERO, tokenScore.totalSupply());
        assertEquals("Stable Token", tokenScore.name());
        assertEquals("STO", tokenScore.symbol());
        assertEquals(ownerWallet.getAddress(), tokenScore.admin());
        assertEquals(BigInteger.valueOf(18), tokenScore.decimals());
        assertEquals(BigInteger.valueOf(50), tokenScore.freeDailyTxLimit());

        BigInteger value = BigInteger.TEN.pow(tokenScore.decimals().intValue());

        // 2. Add Issuers
        LOG.infoEntering("admin add owner as issuer");
        Bytes add=tokenScore.addIssuer(ownerWallet,ownerWallet.getAddress());
        assertSuccess(txHandler.getResult(add));

        // 2. Approve Issuers a amount
        LOG.infoEntering("admin approve owner to mint value amount");
        Bytes approve=tokenScore.approve(ownerWallet,ownerWallet.getAddress(),value);
        assertSuccess(txHandler.getResult(approve));

        tokenScore.getIssuers();

        // 3. mint some tokens
        LOG.infoEntering("mint for owner");
        Bytes mint=tokenScore.mint(ownerWallet,value);
        assertSuccess(txHandler.getResult(mint));
        assertEquals(value, tokenScore.balanceOf(ownerWallet.getAddress()));

        // 4. mint to caller
        LOG.infoEntering("admin approve owner to mint value amount");
        approve=tokenScore.approve(ownerWallet,ownerWallet.getAddress(),value);
        assertSuccess(txHandler.getResult(approve));

        LOG.infoEntering("owner address is whitelisted");
        assertEquals(true,tokenScore.isWhitelisted(ownerWallet.getAddress()));


        LOG.infoEntering("mint for caller");
        Bytes mintTo=tokenScore.mintTo(ownerWallet,caller.getAddress(),value);
        assertSuccess(txHandler.getResult(mintTo));
        assertEquals(value, tokenScore.balanceOf(caller.getAddress()));

        // 4. burn half tokens of caller
        LOG.infoEntering("burn from caller");
        Bytes burn=tokenScore.burn(caller,value.divide(BigInteger.TWO));
        assertSuccess(txHandler.getResult(burn));
        assertEquals(value.divide(BigInteger.TWO), tokenScore.balanceOf(caller.getAddress()));


        // 4. transfer half tokens from owner to caller
        LOG.infoEntering("transfer from owner to caller");
        Bytes transfer=tokenScore.transfer(ownerWallet,caller.getAddress(),value.divide(BigInteger.TWO),"transfer".getBytes());
        assertSuccess(txHandler.getResult(transfer));
        assertEquals(value, tokenScore.balanceOf(caller.getAddress()));
    };

    @Test
    public void add_same_issuer_twice() throws IOException, ResultTimeoutException, TransactionFailureException {
        StableCoinScore tokenScore = StableCoinScore.mustDeploy(txHandler, ownerWallet);

        LOG.infoEntering("admin add owner as issuer");
        Bytes add=tokenScore.addIssuer(ownerWallet,ownerWallet.getAddress());
        assertSuccess(txHandler.getResult(add));

        add=tokenScore.addIssuer(ownerWallet,ownerWallet.getAddress());
        assertFailure(txHandler.getResult(add));
    }

    @Test
    public void transfer_admin_right_check_access() throws IOException, ResultTimeoutException, TransactionFailureException {
        StableCoinScore tokenScore = StableCoinScore.mustDeploy(txHandler, ownerWallet);

        LOG.infoEntering("transfer admin right");
        Bytes adminRight=tokenScore.transferAdminRight(ownerWallet,caller.getAddress());
        assertSuccess(txHandler.getResult(adminRight));

        LOG.infoEntering("owner add owner as issuer");
        Bytes add=tokenScore.addIssuer(ownerWallet,ownerWallet.getAddress());
        assertFailure(txHandler.getResult(add));

        LOG.infoEntering("owner add owner as issuer");
        add=tokenScore.addIssuer(caller,ownerWallet.getAddress());
        assertSuccess(txHandler.getResult(add));
    }

    @Test
    public void check_transactions_when_paused() throws IOException, ResultTimeoutException, TransactionFailureException {
        StableCoinScore tokenScore = StableCoinScore.mustDeploy(txHandler, ownerWallet);

        BigInteger value = BigInteger.TEN.pow(tokenScore.decimals().intValue());

        LOG.infoEntering("admin add owner as issuer");
        Bytes add=tokenScore.addIssuer(ownerWallet,ownerWallet.getAddress());
        assertSuccess(txHandler.getResult(add));

        LOG.infoEntering("admin approve owner to mint value amount");
        Bytes approve=tokenScore.approve(ownerWallet,ownerWallet.getAddress(),value);
        assertSuccess(txHandler.getResult(approve));

        LOG.infoEntering("admin pause the contract");
        Bytes togglePause=tokenScore.togglePause(ownerWallet);
        assertSuccess(txHandler.getResult(togglePause));

        LOG.infoEntering("mint fails when paused");
        Bytes mint=tokenScore.mint(ownerWallet,value);
        assertFailure(txHandler.getResult(mint));

        LOG.infoEntering("burn fails when paused");
        Bytes burn=tokenScore.burn(caller,value.divide(BigInteger.TWO));
        assertFailure(txHandler.getResult(burn));

        LOG.infoEntering("transfer fails when paused");
        Bytes transfer=tokenScore.transfer(ownerWallet,caller.getAddress(),value.divide(BigInteger.TWO),"transfer".getBytes());
        assertFailure(txHandler.getResult(transfer));
    }

}
