package com.alipay.antchain.bridge.plugins.fiscobcos.abi;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes;
import org.fisco.bcos.sdk.v3.codec.datatypes.Event;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.datatypes.Type;
import org.fisco.bcos.sdk.v3.codec.datatypes.TypeReference;
import org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple3;
import org.fisco.bcos.sdk.v3.contract.Contract;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.eventsub.EventSubCallback;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;

@SuppressWarnings("unchecked")
public class ReceiverContract extends Contract {
    public static final String[] BINARY_ARRAY = {"608060405234801561001057600080fd5b506104e2806100206000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c806335cd96e01461005157806389c9b1d91461006f578063c09b261b14610077578063ff098be71461008c575b600080fd5b61005961009f565b60405161006691906102fe565b60405180910390f35b610059610131565b61008a6100853660046103a4565b610140565b005b61008a61009a3660046103a4565b6101d1565b6060600080546100ae9061043c565b80601f01602080910402602001604051908101604052809291908181526020018280546100da9061043c565b80156101275780601f106100fc57610100808354040283529160200191610127565b820191906000526020600020905b81548152906001019060200180831161010a57829003601f168201915b5050505050905090565b6060600180546100ae9061043c565b80516020141561017d5760405162461bcd60e51b815260206004820152600360248201526219992160e91b60448201526064015b60405180910390fd5b8051610190906000906020840190610218565b507f51f92d38e474586a945dac6ef7908ea588cfbe236a616f355039d447309691848383836040516101c493929190610477565b60405180910390a1505050565b8051602014156102095760405162461bcd60e51b815260206004820152600360248201526219992160e91b6044820152606401610174565b80516101909060019060208401905b8280546102249061043c565b90600052602060002090601f016020900481019282610246576000855561028c565b82601f1061025f57805160ff191683800117855561028c565b8280016001018555821561028c579182015b8281111561028c578251825591602001919060010190610271565b5061029892915061029c565b5090565b5b80821115610298576000815560010161029d565b6000815180845260005b818110156102d7576020818501810151868301820152016102bb565b818111156102e9576000602083870101525b50601f01601f19169290920160200192915050565b60208152600061031160208301846102b1565b9392505050565b634e487b7160e01b600052604160045260246000fd5b600067ffffffffffffffff8084111561034957610349610318565b604051601f8501601f19908116603f0116810190828211818310171561037157610371610318565b8160405280935085815286868601111561038a57600080fd5b858560208301376000602087830101525050509392505050565b6000806000606084860312156103b957600080fd5b833567ffffffffffffffff808211156103d157600080fd5b818601915086601f8301126103e557600080fd5b6103f48783356020850161032e565b945060208601359350604086013591508082111561041157600080fd5b508401601f8101861361042357600080fd5b6104328682356020840161032e565b9150509250925092565b600181811c9082168061045057607f821691505b6020821081141561047157634e487b7160e01b600052602260045260246000fd5b50919050565b60608152600061048a60608301866102b1565b84602084015282810360408401526104a281856102b1565b969550505050505056fea2646970667358221220e2959b4e25937a90f4b91a5be897af569c791885b7838df010af0b4ee638da4664736f6c634300080b0033"};

    public static final String BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", BINARY_ARRAY);

    public static final String[] SM_BINARY_ARRAY = {"608060405234801561001057600080fd5b506104e4806100206000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c8063281cf5e6146100515780638a58513714610066578063bdb560a614610084578063e5b813ad1461008c575b600080fd5b61006461005f36600461033f565b61009f565b005b61006e610131565b60405161007b9190610424565b60405180910390f35b61006e6101c3565b61006461009a36600461033f565b6101d2565b8051602014156100dd57604051636381e58960e11b815260206004820152600360248201526219992160e91b60448201526064015b60405180910390fd5b80516100f090600090602084019061021a565b507fb3585d51c91acc32851a939fc4fdfcd2d40d9a7fe411adc66400a9d411677ea08383836040516101249392919061043e565b60405180910390a1505050565b60606001805461014090610473565b80601f016020809104026020016040519081016040528092919081815260200182805461016c90610473565b80156101b95780601f1061018e576101008083540402835291602001916101b9565b820191906000526020600020905b81548152906001019060200180831161019c57829003601f168201915b5050505050905090565b60606000805461014090610473565b80516020141561020b57604051636381e58960e11b815260206004820152600360248201526219992160e91b60448201526064016100d4565b80516100f09060019060208401905b82805461022690610473565b90600052602060002090601f016020900481019282610248576000855561028e565b82601f1061026157805160ff191683800117855561028e565b8280016001018555821561028e579182015b8281111561028e578251825591602001919060010190610273565b5061029a92915061029e565b5090565b5b8082111561029a576000815560010161029f565b63b95aa35560e01b600052604160045260246000fd5b600067ffffffffffffffff808411156102e4576102e46102b3565b604051601f8501601f19908116603f0116810190828211818310171561030c5761030c6102b3565b8160405280935085815286868601111561032557600080fd5b858560208301376000602087830101525050509392505050565b60008060006060848603121561035457600080fd5b833567ffffffffffffffff8082111561036c57600080fd5b818601915086601f83011261038057600080fd5b61038f878335602085016102c9565b94506020860135935060408601359150808211156103ac57600080fd5b508401601f810186136103be57600080fd5b6103cd868235602084016102c9565b9150509250925092565b6000815180845260005b818110156103fd576020818501810151868301820152016103e1565b8181111561040f576000602083870101525b50601f01601f19169290920160200192915050565b60208152600061043760208301846103d7565b9392505050565b60608152600061045160608301866103d7565b846020840152828103604084015261046981856103d7565b9695505050505050565b600181811c9082168061048757607f821691505b602082108114156104a85763b95aa35560e01b600052602260045260246000fd5b5091905056fea26469706673582212208fb561188637d89b634f5ff4e6e16b2b5ba2386debcecf92189ea93e776b091b64736f6c634300080b0033"};

    public static final String SM_BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", SM_BINARY_ARRAY);

    public static final String[] ABI_ARRAY = {"[{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"internalType\":\"string\",\"name\":\"key\",\"type\":\"string\"},{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"value\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"string\",\"name\":\"enterprise\",\"type\":\"string\"}],\"name\":\"amNotify\",\"type\":\"event\"},{\"inputs\":[],\"name\":\"getLastMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"getLastUnorderedMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"domain_name\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"author\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"recvMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"domain_name\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"author\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"recvUnorderedMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]"};

    public static final String ABI = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", ABI_ARRAY);

    public static final String FUNC_GETLASTMSG = "getLastMsg";

    public static final String FUNC_GETLASTUNORDEREDMSG = "getLastUnorderedMsg";

    public static final String FUNC_RECVMESSAGE = "recvMessage";

    public static final String FUNC_RECVUNORDEREDMESSAGE = "recvUnorderedMessage";

    public static final Event AMNOTIFY_EVENT = new Event("amNotify", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}));
    ;

    protected ReceiverContract(String contractAddress, Client client, CryptoKeyPair credential) {
        super(getBinary(client.getCryptoSuite()), contractAddress, client, credential);
    }

    public static String getBinary(CryptoSuite cryptoSuite) {
        return (cryptoSuite.getCryptoTypeConfig() == CryptoType.ECDSA_TYPE ? BINARY : SM_BINARY);
    }

    public static String getABI() {
        return ABI;
    }

    public List<AmNotifyEventResponse> getAmNotifyEvents(TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(AMNOTIFY_EVENT, transactionReceipt);
        ArrayList<AmNotifyEventResponse> responses = new ArrayList<AmNotifyEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AmNotifyEventResponse typedResponse = new AmNotifyEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.key = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.value = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.enterprise = (String) eventValues.getNonIndexedValues().get(2).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeAmNotifyEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(AMNOTIFY_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeAmNotifyEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(AMNOTIFY_EVENT);
        subscribeEvent(topic0,callback);
    }

    public byte[] getLastMsg() throws ContractException {
        final Function function = new Function(FUNC_GETLASTMSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodGetLastMsgRawFunction() throws ContractException {
        final Function function = new Function(FUNC_GETLASTMSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public byte[] getLastUnorderedMsg() throws ContractException {
        final Function function = new Function(FUNC_GETLASTUNORDEREDMSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodGetLastUnorderedMsgRawFunction() throws ContractException {
        final Function function = new Function(FUNC_GETLASTUNORDEREDMSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public TransactionReceipt recvMessage(String domain_name, byte[] author, byte[] message) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodRecvMessageRawFunction(String domain_name, byte[] author,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForRecvMessage(String domain_name, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String recvMessage(String domain_name, byte[] author, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<String, byte[], byte[]> getRecvMessageInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<String, byte[], byte[]>(

                (String) results.get(0).getValue(), 
                (byte[]) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public TransactionReceipt recvUnorderedMessage(String domain_name, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodRecvUnorderedMessageRawFunction(String domain_name, byte[] author,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForRecvUnorderedMessage(String domain_name, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String recvUnorderedMessage(String domain_name, byte[] author, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain_name), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<String, byte[], byte[]> getRecvUnorderedMessageInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<String, byte[], byte[]>(

                (String) results.get(0).getValue(), 
                (byte[]) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public static ReceiverContract load(String contractAddress, Client client,
            CryptoKeyPair credential) {
        return new ReceiverContract(contractAddress, client, credential);
    }

    public static ReceiverContract deploy(Client client, CryptoKeyPair credential) throws
            ContractException {
        return deploy(ReceiverContract.class, client, credential, getBinary(client.getCryptoSuite()), getABI(), null, null);
    }

    public static class AmNotifyEventResponse {
        public TransactionReceipt.Logs log;

        public String key;

        public byte[] value;

        public String enterprise;
    }
}
