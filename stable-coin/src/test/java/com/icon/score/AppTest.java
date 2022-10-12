package com.icon.score;

import com.iconloop.score.test.Score;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.function.Executable;
import java.math.BigInteger;
import java.util.List;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.ServiceManager;
import score.Address;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.spy;

public class AppTest extends TestBase {

    public static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private Score tokenScore;
    public StableCoin scoreSpy;
    private static final String name = "StableToken";
    private static final String symbol = "STO";
    private static final BigInteger decimals = BigInteger.valueOf(18);
    private static final int nIssuers = 2;
    protected final Address EOA_ZERO = Address.fromString("hx0000000000000000000000000000000000000000");


    @BeforeEach
    public void setup() throws Exception {
        tokenScore = sm.deploy(owner,StableCoin.class, name, symbol, decimals,owner.getAddress(),nIssuers);
//        System.out.println(tokenScore.call("name"));
        StableCoin t = (StableCoin) tokenScore.getInstance();
        scoreSpy = spy(t);
//        mockScoreClients();
        tokenScore.setInstance(scoreSpy);
    }
    @Test
    void testName() {
        assertEquals(name,tokenScore.call("name"));
    }
    @Test
    void testSymbol() {
        assertEquals(symbol,tokenScore.call("symbol"));
    }

    @Test
    void admin(){
        assertEquals(owner.getAddress(),tokenScore.call("getAdmin"));
    }

    @Test
    void testDecimals(){
        assertEquals(decimals,tokenScore.call("decimals"));
    }

    @Test()
    void addRemoveIssuers(){
        //not by owner
        Account A = sm.createAccount();
        Account B = sm.createAccount();
        Executable addIssuerNotByAdmin = () -> tokenScore.invoke(A,"addIssuer",A.getAddress());
        String expectedErrorMessage = "Only admin can add issuer";
        expectErrorMessage(addIssuerNotByAdmin,expectedErrorMessage);

        //add issuers by owner
        tokenScore.invoke(owner,"addIssuer",A.getAddress());
        tokenScore.invoke(owner,"addIssuer",B.getAddress());

        //get issuers
        Address[] issuers =(Address[]) tokenScore.call("getIssuers");
        assertEquals(2,issuers.length);
        assertEquals(A.getAddress(),issuers[0]);
        assertEquals(B.getAddress(),issuers[1]);

        //add already issued issuers
        Executable alreadyissued = () -> tokenScore.invoke(owner,"addIssuer",A.getAddress());
        expectedErrorMessage = A.getAddress() + " is already an issuer";
        expectErrorMessage(alreadyissued,expectedErrorMessage);

        //remove issuers by not admin
        Executable removeIssuerByNonAdmin = () -> tokenScore.invoke(A,"removeIssuer",A.getAddress());
        expectedErrorMessage = "Only admin can remove issuer";
        expectErrorMessage(removeIssuerByNonAdmin,expectedErrorMessage);

        //remove issuer A
        tokenScore.invoke(owner,"removeIssuer",A.getAddress());

        //try to remove A again
        Executable removeNotAnIssuer = () -> tokenScore.invoke(owner,"removeIssuer",A.getAddress());
        expectedErrorMessage = A.getAddress() + " not an issuer";
        expectErrorMessage(removeNotAnIssuer,expectedErrorMessage);

        issuers =(Address[]) tokenScore.call("getIssuers");
        assertEquals(1,issuers.length);
        assertEquals(B.getAddress(),issuers[0]);
    }

    @Test
    void changeAdmin(){
        //not by admin
        Account A = sm.createAccount();
        Account B = sm.createAccount();
        Executable changeAdminNotByAdmin = () -> tokenScore.invoke(A,"transferAdminRight",A.getAddress());
        String expectedErrorMessage = "Only admin can transfer their admin right";
        expectErrorMessage(changeAdminNotByAdmin,expectedErrorMessage);

        //change admin and get admin
        tokenScore.invoke(owner,"transferAdminRight",A.getAddress());
        assertEquals(A.getAddress(),tokenScore.call("getAdmin"));

        //check whether previous admin lost admin rights
        Executable addIssuerNotByAdmin = () -> tokenScore.invoke(owner,"addIssuer",A.getAddress());
        expectedErrorMessage = "Only admin can add issuer";
        expectErrorMessage(addIssuerNotByAdmin,expectedErrorMessage);

    }

    @Test
    void changeFreeDailyTxLimit(){
        //not by admin
        Account A = sm.createAccount();
        Executable NotByAdmin = () -> tokenScore.invoke(A,"changeFreeDailyTxLimit",BigInteger.TWO);
        String expectedErrorMessage = "Only admin can change free daily transaction limit";
        expectErrorMessage(NotByAdmin,expectedErrorMessage);

        //change limit to zero
//        Executable zeroLimit = () -> tokenScore.invoke(A,"changeFreeDailyTxLimit",BigInteger.ZERO);
//        expectedErrorMessage = "Free daily transaction limit cannot be under 0.";
//        expectErrorMessage(zeroLimit,expectedErrorMessage);

        //change to 2
//        tokenScore.invoke(owner,"changeFreeDailyTxLimit",BigInteger.TWO);
    }

    @Test
    void mint_test_by_not_nonIssuers(){
        Account A = sm.createAccount();
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        Executable mintNotByIssuerCall = () -> tokenScore.invoke(A,"mintTo", owner.getAddress(), value);
        String expectedErrorMessage = "Only issuers can mint";
        expectErrorMessage(mintNotByIssuerCall,expectedErrorMessage);
    }

    @Test
    void mint_test_to_zero_Address(){
        tokenScore.invoke(owner,"addIssuer",owner.getAddress());
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        Executable mintToZero = () -> tokenScore.invoke(owner,"mintTo", EOA_ZERO, value);
        String expectedErrorMessage = "Cannot mint to zero address";
        expectErrorMessage(mintToZero,expectedErrorMessage);
    }

    @Test
    void mint_with_allowance_amount_exceed(){
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        //add issuer
        tokenScore.invoke(owner,"addIssuer",owner.getAddress());
        //add allowance
        tokenScore.invoke(owner,"approve",owner.getAddress(),value);

        Executable allowanceExceed = () -> tokenScore.invoke(owner,"mint", value.add(BigInteger.ONE));
        String expectedErrorMessage = "Allowance amount to mint exceed";
        expectErrorMessage(allowanceExceed,expectedErrorMessage);
    }

    @Test
    void mint_in_paused_state(){
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        //add issuer
        tokenScore.invoke(owner,"addIssuer",owner.getAddress());
        //pause
        tokenScore.invoke(owner,"togglePause");

        Executable pausedMint = () -> tokenScore.invoke(owner,"mint", value.add(BigInteger.ONE));
        String expectedErrorMessage = "Cannot mint when paused";
        expectErrorMessage(pausedMint,expectedErrorMessage);

    }

    @Test
    void mint_flow(){
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        //check if whitelisted
        assertEquals(false,tokenScore.call("isWhitelisted",owner.getAddress()));
        //add issuer
        tokenScore.invoke(owner,"addIssuer",owner.getAddress());
        //add allowance
        tokenScore.invoke(owner,"approve",owner.getAddress(),value);
        //mint
        tokenScore.invoke(owner,"mint", value);
        //check balance
        assertEquals(value,tokenScore.call("balanceOf",owner.getAddress()));
        //check totalSupply
        assertEquals(value,tokenScore.call("totalSupply"));
        //check if whitelisted
        assertEquals(true,tokenScore.call("isWhitelisted",owner.getAddress()));
    }

    @Test
    void burn_test_with_zero_amount(){
        Executable burn = () -> tokenScore.invoke(owner,"burn", BigInteger.ZERO);
        String expectedErrorMessage = "Amount to burn should be greater than zero";
        expectErrorMessage(burn,expectedErrorMessage);
    }

    @Test
    void burn_with_insufficient_amount(){
        mint_flow();
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());

        Executable insufficientBalance = () -> tokenScore.invoke(owner,"burn", value.add(BigInteger.ONE));
        String expectedErrorMessage = "Insufficient balance to burn";
        expectErrorMessage(insufficientBalance,expectedErrorMessage);
    }

    @Test
    void burn_in_paused_state(){
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        mint_flow();
        //pause
        tokenScore.invoke(owner,"togglePause");

        Executable pausedMint = () -> tokenScore.invoke(owner,"burn", value);
        String expectedErrorMessage = "Cannot burn when paused";
        expectErrorMessage(pausedMint,expectedErrorMessage);

    }

    @Test
    void burn_flow(){
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        mint_flow();
        //burn
        tokenScore.invoke(owner,"burn", value);
        //check balance
        assertEquals(BigInteger.ZERO,tokenScore.call("balanceOf",owner.getAddress()));
        //check totalSupply
        assertEquals(BigInteger.ZERO,tokenScore.call("totalSupply"));
    }

    @Test
    void transfer() {
        Account A = sm.createAccount();
        BigInteger value = BigInteger.TEN.pow(decimals.intValue());
        tokenScore.invoke(owner, "transfer", A.getAddress(), value, "to alice".getBytes());
        owner.subtractBalance(symbol, value);
        assertEquals(owner.getBalance(symbol),
                tokenScore.call("balanceOf", tokenScore.getOwner().getAddress()));
        assertEquals(value,
                tokenScore.call("balanceOf", A.getAddress()));

        // transfer self
        tokenScore.invoke(A, "transfer", A.getAddress(), value, "self transfer".getBytes());
        assertEquals(value, tokenScore.call("balanceOf", A.getAddress()));
    }

    private void expectErrorMessage(Executable contractCall, String errorMessage) {
        AssertionError e = Assertions.assertThrows(AssertionError.class, contractCall);
        assertEquals(errorMessage, e.getMessage());
    }

}