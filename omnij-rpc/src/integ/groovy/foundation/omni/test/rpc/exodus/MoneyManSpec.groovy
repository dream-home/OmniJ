package foundation.omni.test.rpc.exodus

import foundation.omni.OmniDivisibleValue
import foundation.omni.test.MoneyMan
import org.bitcoinj.core.Address
import foundation.omni.BaseRegTestSpec
import foundation.omni.net.OmniRegTestParams
import foundation.omni.rpc.OmniClient
import org.bitcoinj.core.Coin
import spock.lang.Shared
import spock.lang.Stepwise

import static foundation.omni.CurrencyID.MSC
import static foundation.omni.CurrencyID.TMSC

@Stepwise
class MoneyManSpec extends BaseRegTestSpec {

    final static Coin sendAmount = 10.btc
    final static Coin extraAmount = 0.10.btc
    final static Coin faucetBTC = 10.btc
    final static OmniDivisibleValue faucetMSC = 1000.divisible
    final static OmniDivisibleValue simpleSendAmount = 1.divisible

    @Shared
    Address faucetAddress

    def setup() {
        faucetAddress = createFundedAddress(faucetBTC, faucetMSC)
    }

    def "Send BTC to an address to get MSC and TMSC"() {
        // This test is truly a test of MoneyMan functionality
        when: "we create a new address for Omni and send some BTC to it"
        Address testAddress = getNewAddress()
        def txid = sendToAddress(testAddress, sendAmount + extraAmount + stdTxFee)

        and: "we generate a block"
        generateBlock()

        then: "we have the correct amount of BTC in faucetAddress's account"
        getBitcoinBalance(testAddress) == sendAmount + extraAmount + stdTxFee

        when: "We send the BTC to the moneyManAddress and generate a block"
        txid = sendBitcoin(testAddress, OmniRegTestParams.get().moneyManAddress, sendAmount)
        generateBlock()
        def tx = client.getTransaction(txid)

        then: "transaction was confirmed"
        tx.confirmations == 1

        and: "The balances for the account we just sent MSC to is correct"
        getBitcoinBalance(testAddress) == extraAmount
        omniGetBalance(testAddress, MSC).balance ==  MoneyMan.toOmni(sendAmount).bigDecimalValue()
        omniGetBalance(testAddress, TMSC).balance == MoneyMan.toOmni(sendAmount).bigDecimalValue()
    }

    def "check Spec setup"() {
        // This test is really an integration test of createFundedAddress()
        expect:
        getBitcoinBalance(faucetAddress) == faucetBTC
        omniGetBalance(faucetAddress, MSC).balance == MoneyMan.toOmni(sendAmount).bigDecimalValue()
        omniGetBalance(faucetAddress, TMSC).balance == MoneyMan.toOmni(sendAmount).bigDecimalValue()
    }

    def "Simple send MSC from one address to another" () {
        // This test either duplicates or should be moved to MSCSimpleSendSpec

        when: "we send MSC"
        def senderBalance = omniGetBalance(faucetAddress, MSC)
        def toAddress = getNewAddress()
        def txid = client.omniSend(faucetAddress, toAddress, MSC, simpleSendAmount.bigDecimalValue())
        def tx = client.getTransaction(txid)

        then: "we got a non-zero transaction id"
        txid != OmniClient.zeroHash
        tx

        when: "a block is generated"
        generateBlock()
        def newSenderBalance = client.omniGetBalance(faucetAddress, MSC)
        def receiverBalance = client.omniGetBalance(toAddress, MSC)

        then: "the toAddress has the correct MSC balance and source address is reduced by right amount"
        receiverBalance.balance == simpleSendAmount.bigDecimalValue()
        newSenderBalance.balance == senderBalance.balance - simpleSendAmount.bigDecimalValue()
    }

    def "Send MSC back to same adddress" () {
        // This test either duplicates or should be moved to MSCSimpleSendSpec

        when: "we send MSC"
        def wealthyBalance = omniGetBalance(faucetAddress, MSC).balance
        def txid = omniSend(faucetAddress, faucetAddress, MSC, 10.12345678)

        then: "we got a non-zero transaction id"
        txid != OmniClient.zeroHash

        when: "a block is generated"
        generateBlock()
        def newWealthyBalance = omniGetBalance(faucetAddress, MSC).balance

        then: "balance is unchanged"
        newWealthyBalance == wealthyBalance
    }
}