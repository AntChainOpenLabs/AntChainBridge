package com.alipay.antchain.bridge.plugins.fiscobcos.abi;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.codec.datatypes.Address;
import org.fisco.bcos.sdk.v3.codec.datatypes.Bool;
import org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes;
import org.fisco.bcos.sdk.v3.codec.datatypes.Event;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.datatypes.Type;
import org.fisco.bcos.sdk.v3.codec.datatypes.TypeReference;
import org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple1;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple3;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple4;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple6;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple7;
import org.fisco.bcos.sdk.v3.contract.Contract;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.eventsub.EventSubCallback;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;

@SuppressWarnings("unchecked")
public class AppContract extends Contract {
    public static final String[] BINARY_ARRAY = {"608060405234801561001057600080fd5b5061001a3361001f565b61006f565b600080546001600160a01b038381166001600160a01b0319831681178455604051919092169283917f8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e09190a35050565b6111a78061007e6000396000f3fe608060405234801561001057600080fd5b50600436106101585760003560e01c8063a1c5324d116100c3578063e5512e971161007c578063e5512e9714610285578063eff2a07614610298578063f2fde38b146102ab578063f6d750e4146102be578063f76f703b146102d1578063ff098be7146102e457600080fd5b8063a1c5324d1461023d578063a25ae14a14610246578063aec2fc141461024e578063c09b261b14610256578063c1cecc5a14610269578063d7686d5c1461027c57600080fd5b8063715018a611610115578063715018a6146101ed5780637260ca9c146101f557806381c3f533146101fe57806389c9b1d9146102115780638da5cb5b146102195780639670efcb1461022a57600080fd5b80630a9d793d1461015d57806335cd96e014610172578063387868ae146101905780633fecfe3f146101bb57806358e28153146101ce5780635ee20956146101d6575b600080fd5b61017061016b366004610b9f565b6102f7565b005b61017a61034c565b6040516101879190610c1c565b60405180910390f35b6003546101a3906001600160a01b031681565b6040516001600160a01b039091168152602001610187565b61017a6101c9366004610c2f565b6103de565b61017a610497565b6101df60075481565b604051908152602001610187565b6101706104a4565b6101df60055481565b61017061020c366004610cf4565b6104da565b61017a61055f565b6000546001600160a01b03166101a3565b61017a610238366004610c2f565b61056e565b6101df60045481565b61017a61058a565b61017a610597565b610170610264366004610d7b565b6105a4565b610170610277366004610d7b565b61067c565b6101df60065481565b610170610293366004610e19565b61074a565b6101706102a6366004610cf4565b610798565b6101706102b9366004610b9f565b61081d565b6101706102cc366004610eb0565b6108b8565b6101706102df366004610d7b565b61091d565b6101706102f2366004610d7b565b6109eb565b6000546001600160a01b0316331461032a5760405162461bcd60e51b815260040161032190610f6d565b60405180910390fd5b600380546001600160a01b0319166001600160a01b0392909216919091179055565b60606009805461035b90610fa2565b80601f016020809104026020016040519081016040528092919081815260200182805461038790610fa2565b80156103d45780601f106103a9576101008083540402835291602001916103d4565b820191906000526020600020905b8154815290600101906020018083116103b757829003601f168201915b5050505050905090565b600260205281600052604060002081815481106103fa57600080fd5b9060005260206000200160009150915050805461041690610fa2565b80601f016020809104026020016040519081016040528092919081815260200182805461044290610fa2565b801561048f5780601f106104645761010080835404028352916020019161048f565b820191906000526020600020905b81548152906001019060200180831161047257829003601f168201915b505050505081565b6008805461041690610fa2565b6000546001600160a01b031633146104ce5760405162461bcd60e51b815260040161032190610f6d565b6104d86000610ab6565b565b6003546040516391198fcd60e01b81526001600160a01b039091169081906391198fcd90610512908790899088908890600401610fdd565b6020604051808303816000875af1158015610531573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610555919061101b565b6004555050505050565b6060600a805461035b90610fa2565b600160205281600052604060002081815481106103fa57600080fd5b600a805461041690610fa2565b6009805461041690610fa2565b6003546001600160a01b031633146105f35760405162461bcd60e51b815260206004820152601260248201527124a72b20a624a22fa822a926a4a9a9a4a7a760711b6044820152606401610321565b6000828152600160208181526040832080549283018155835291829020835161062493919092019190840190610b06565b508051610638906009906020840190610b06565b507f7b59e766dd02d6ce4e574f0ab75dfc4b180c90deb50dd9dbdbc65768abcf80f1838383600160405161066f9493929190611034565b60405180910390a1505050565b6003546040516360e7662d60e11b81526001600160a01b039091169063c1cecc5a906106b090869086908690600401611073565b600060405180830381600087803b1580156106ca57600080fd5b505af11580156106de573d6000803e3d6000fd5b505050600083815260026020908152604082208054600181018255908352918190208451610713945092019190840190610b06565b507fbbe83d8459c305cf51f2425b93e693dcfaaf60f22766486b0983c905b98ead40838383600060405161066f9493929190611034565b7fa8bb25943cddb58193ff50af54633a11fcff3e922840a1c4f2964f2fff27a025868686868686604051610783969594939291906110a8565b60405180910390a15050506006929092555050565b6003546040516302e546d360e11b81526001600160a01b039091169081906305ca8da6906107d0908790899088908890600401610fdd565b6020604051808303816000875af11580156107ef573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610813919061101b565b6005555050505050565b6000546001600160a01b031633146108475760405162461bcd60e51b815260040161032190610f6d565b6001600160a01b0381166108ac5760405162461bcd60e51b815260206004820152602660248201527f4f776e61626c653a206e6577206f776e657220697320746865207a65726f206160448201526564647265737360d01b6064820152608401610321565b6108b581610ab6565b50565b7f67644860f47ea033ed40e06c5d59a6fc642d999c52b2fd29a57d476db4989d81878787878787876040516108f39796959493929190611102565b60405180910390a160078790558051610913906008906020840190610b06565b5050505050505050565b60035460405163f76f703b60e01b81526001600160a01b039091169063f76f703b9061095190869086908690600401611073565b600060405180830381600087803b15801561096b57600080fd5b505af115801561097f573d6000803e3d6000fd5b5050506000838152600260209081526040822080546001810182559083529181902084516109b4945092019190840190610b06565b507fbbe83d8459c305cf51f2425b93e693dcfaaf60f22766486b0983c905b98ead40838383600160405161066f9493929190611034565b6003546001600160a01b03163314610a3a5760405162461bcd60e51b815260206004820152601260248201527124a72b20a624a22fa822a926a4a9a9a4a7a760711b6044820152606401610321565b60008281526001602081815260408320805492830181558352918290208351610a6b93919092019190840190610b06565b508051610a7f90600a906020840190610b06565b507f7b59e766dd02d6ce4e574f0ab75dfc4b180c90deb50dd9dbdbc65768abcf80f1838383600060405161066f9493929190611034565b600080546001600160a01b038381166001600160a01b0319831681178455604051919092169283917f8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e09190a35050565b828054610b1290610fa2565b90600052602060002090601f016020900481019282610b345760008555610b7a565b82601f10610b4d57805160ff1916838001178555610b7a565b82800160010185558215610b7a579182015b82811115610b7a578251825591602001919060010190610b5f565b50610b86929150610b8a565b5090565b5b80821115610b865760008155600101610b8b565b600060208284031215610bb157600080fd5b81356001600160a01b0381168114610bc857600080fd5b9392505050565b6000815180845260005b81811015610bf557602081850181015186830182015201610bd9565b81811115610c07576000602083870101525b50601f01601f19169290920160200192915050565b602081526000610bc86020830184610bcf565b60008060408385031215610c4257600080fd5b50508035926020909101359150565b634e487b7160e01b600052604160045260246000fd5b600082601f830112610c7857600080fd5b813567ffffffffffffffff80821115610c9357610c93610c51565b604051601f8301601f19908116603f01168101908282118183101715610cbb57610cbb610c51565b81604052838152866020858801011115610cd457600080fd5b836020870160208301376000602085830101528094505050505092915050565b60008060008060808587031215610d0a57600080fd5b84359350602085013567ffffffffffffffff80821115610d2957600080fd5b610d3588838901610c67565b9450604087013591508115158214610d4c57600080fd5b90925060608601359080821115610d6257600080fd5b50610d6f87828801610c67565b91505092959194509250565b600080600060608486031215610d9057600080fd5b833567ffffffffffffffff80821115610da857600080fd5b610db487838801610c67565b9450602086013593506040860135915080821115610dd157600080fd5b50610dde86828701610c67565b9150509250925092565b803563ffffffff81168114610dfc57600080fd5b919050565b803567ffffffffffffffff81168114610dfc57600080fd5b60008060008060008060c08789031215610e3257600080fd5b86359550602087013567ffffffffffffffff80821115610e5157600080fd5b610e5d8a838b01610c67565b965060408901359550610e7260608a01610de8565b9450610e8060808a01610e01565b935060a0890135915080821115610e9657600080fd5b50610ea389828a01610c67565b9150509295509295509295565b600080600080600080600060e0888a031215610ecb57600080fd5b87359650602088013567ffffffffffffffff80821115610eea57600080fd5b610ef68b838c01610c67565b975060408a01359650610f0b60608b01610de8565b9550610f1960808b01610e01565b945060a08a0135915080821115610f2f57600080fd5b610f3b8b838c01610c67565b935060c08a0135915080821115610f5157600080fd5b50610f5e8a828b01610c67565b91505092959891949750929550565b6020808252818101527f4f776e61626c653a2063","616c6c6572206973206e6f7420746865206f776e6572604082015260600190565b600181811c90821680610fb657607f821691505b60208210811415610fd757634e487b7160e01b600052602260045260246000fd5b50919050565b608081526000610ff06080830187610bcf565b856020840152841515604084015282810360608401526110108185610bcf565b979650505050505050565b60006020828403121561102d57600080fd5b5051919050565b6080815260006110476080830187610bcf565b856020840152828103604084015261105f8186610bcf565b915050821515606083015295945050505050565b6060815260006110866060830186610bcf565b846020840152828103604084015261109e8185610bcf565b9695505050505050565b86815260c0602082015260006110c160c0830188610bcf565b86604084015263ffffffff8616606084015267ffffffffffffffff8516608084015282810360a08401526110f58185610bcf565b9998505050505050505050565b87815260e06020820152600061111b60e0830189610bcf565b87604084015263ffffffff8716606084015267ffffffffffffffff8616608084015282810360a084015261114f8186610bcf565b905082810360c08401526111638185610bcf565b9a995050505050505050505056fea26469706673582212208efa56bf468df8098bb568198d902a3d13b350d9a685d7b7d5e261aaacccfed764736f6c634300080b0033"};

    public static final String BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", BINARY_ARRAY);

    public static final String[] SM_BINARY_ARRAY = {"608060405234801561001057600080fd5b5061001a3361001f565b61006f565b600080546001600160a01b038381166001600160a01b0319831681178455604051919092169283917f5c7c30d4a0f08950cb23be4132957b357fa5dfdb0fcf218f81b86a1c036e47d09190a35050565b6111a98061007e6000396000f3fe608060405234801561001057600080fd5b50600436106101585760003560e01c806390b28686116100c3578063d84ecd691161007c578063d84ecd691461028c578063d86e29e21461029f578063e2df53a9146102a7578063e5b813ad146102ba578063f985484a146102cd578063fbec4563146102e057600080fd5b806390b28686146102445780639c1b7bb214610257578063b31d332914610260578063bdb560a614610268578063d417dd1814610270578063d6175d1a1461028357600080fd5b80632d0da185116101155780632d0da185146101eb5780634ae89fd2146101f45780635089e2c8146101fc5780635b3040451461022157806365699d8d146102295780638a5851371461023c57600080fd5b80630204ebc21461015d578063084e7508146101725780630a3b07e81461019b57806316cad12a146101b25780632097809b146101c5578063281cf5e6146101d8575b600080fd5b61017061016b366004610c44565b6102f3565b005b610185610180366004610cb1565b6103ce565b6040516101929190610d20565b60405180910390f35b6101a460075481565b604051908152602001610192565b6101706101c0366004610d3a565b610487565b6101706101d3366004610d3a565b61052d565b6101706101e6366004610c44565b61057a565b6101a460065481565b610185610646565b6000546001600160a01b03165b6040516001600160a01b039091168152602001610192565b610185610653565b610170610237366004610c44565b610660565b61018561072e565b610185610252366004610cb1565b6107c0565b6101a460045481565b6101856107dc565b6101856107e9565b61017061027e366004610d94565b6107f8565b6101a460055481565b600354610209906001600160a01b031681565b61017061085d565b6101706102b5366004610e51565b610894565b6101706102c8366004610c44565b610919565b6101706102db366004610e51565b6109e5565b6101706102ee366004610ed8565b610a6a565b60035460405163010275e160e11b81526001600160a01b0390911690630204ebc29061032790869086908690600401610f6f565b600060405180830381600087803b15801561034157600080fd5b505af1158015610355573d6000803e3d6000fd5b50505060008381526002602090815260408220805460018101825590835291819020845161038a945092019190840190610b08565b507f200ed2971aa003016354f781cf280ada45672de3dc8c36583854d0bfe8b33a6083838360006040516103c19493929190610fa4565b60405180910390a1505050565b600260205281600052604060002081815481106103ea57600080fd5b9060005260206000200160009150915050805461040690610fe3565b80601f016020809104026020016040519081016040528092919081815260200182805461043290610fe3565b801561047f5780601f106104545761010080835404028352916020019161047f565b820191906000526020600020905b81548152906001019060200180831161046257829003601f168201915b505050505081565b6000546001600160a01b031633146104bb57604051636381e58960e11b81526004016104b29061101e565b60405180910390fd5b6001600160a01b03811661052157604051636381e58960e11b815260206004820152602660248201527f4f776e61626c653a206e6577206f776e657220697320746865207a65726f206160448201526564647265737360d01b60648201526084016104b2565b61052a81610ab8565b50565b6000546001600160a01b0316331461055857604051636381e58960e11b81526004016104b29061101e565b600380546001600160a01b0319166001600160a01b0392909216919091179055565b6003546001600160a01b031633146105ca57604051636381e58960e11b815260206004820152601260248201527124a72b20a624a22fa822a926a4a9a9a4a7a760711b60448201526064016104b2565b600082815260016020818152604083208054928301815583529182902083516105fb93919092019190840190610b08565b50805161060f906009906020840190610b08565b507f96dd7a715fb188abef0d5ab36bd03d64c6457c75f997607aa73f9c3d85700ba583838360016040516103c19493929190610fa4565b600a805461040690610fe3565b6008805461040690610fe3565b6003546040516365699d8d60e01b81526001600160a01b03909116906365699d8d9061069490869086908690600401610f6f565b600060405180830381600087803b1580156106ae57600080fd5b505af11580156106c2573d6000803e3d6000fd5b5050506000838152600260209081526040822080546001810182559083529181902084516106f7945092019190840190610b08565b507f200ed2971aa003016354f781cf280ada45672de3dc8c36583854d0bfe8b33a6083838360016040516103c19493929190610fa4565b6060600a805461073d90610fe3565b80601f016020809104026020016040519081016040528092919081815260200182805461076990610fe3565b80156107b65780601f1061078b576101008083540402835291602001916107b6565b820191906000526020600020905b81548152906001019060200180831161079957829003601f168201915b5050505050905090565b600160205281600052604060002081815481106103ea57600080fd5b6009805461040690610fe3565b60606009805461073d90610fe3565b7f7c428ded023c093a8e5b0442e2d50dac9ab81475f660a28b1cd1a028ffd0d991878787878787876040516108339796959493929190611053565b60405180910390a160078790558051610853906008906020840190610b08565b5050505050505050565b6000546001600160a01b0316331461088857604051636381e58960e11b81526004016104b29061101e565b6108926000610ab8565b565b60035460405163f7d579c760e01b81526001600160a01b0390911690819063f7d579c7906108cc9087908990889088906004016110c2565b6020604051808303816000875af11580156108eb573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061090f9190611100565b6005555050505050565b6003546001600160a01b0316331461096957604051636381e58960e11b815260206004820152601260248201527124a72b20a624a22fa822a926a4a9a9a4a7a760711b60448201526064016104b2565b6000828152600160208181526040832080549283018155835291829020835161099a93919092019190840190610b08565b5080516109ae90600a906020840190610b08565b507f96dd7a715fb188abef0d5ab36bd03d64c6457c75f997607aa73f9c3d85700ba583838360006040516103c19493929190610fa4565b600354604051631839bef160e21b81526001600160a01b039091169081906360e6fbc490610a1d9087908990889088906004016110c2565b6020604051808303816000875af1158015610a3c573d6000803e3d6000fd5b505050506040513d601f19601f82011682018060405250810190610a609190611100565b6004555050505050565b7fa7647aaf8a6699c6874d335ef4930e14945244cf7d83698864123d18f37c0a72868686868686604051610aa396959493929190611119565b60405180910390a15050506006929092555050565b600080546001600160a01b038381166001600160a01b0319831681178455604051919092169283917f5c7c30d4a0f08950cb23be4132957b357fa5dfdb0fcf218f81b86a1c036e47d09190a35050565b828054610b1490610fe3565b90600052602060002090601f016020900481019282610b365760008555610b7c565b82601f10610b4f57805160ff1916838001178555610b7c565b82800160010185558215610b7c579182015b82811115610b7c578251825591602001919060010190610b61565b50610b88929150610b8c565b5090565b5b80821115610b885760008155600101610b8d565b63b95aa35560e01b600052604160045260246000fd5b600082601f830112610bc857600080fd5b813567ffffffffffffffff80821115610be357610be3610ba1565b604051601f8301601f19908116603f01168101908282118183101715610c0b57610c0b610ba1565b81604052838152866020858801011115610c2457600080fd5b836020870160208301376000602085830101528094505050505092915050565b600080600060608486031215610c5957600080fd5b833567ffffffffffffffff80821115610c7157600080fd5b610c7d87838801610bb7565b9450602086013593506040860135915080821115610c9a57600080fd5b50610ca786828701610bb7565b9150509250925092565b60008060408385031215610cc457600080fd5b50508035926020909101359150565b6000815180845260005b81811015610cf957602081850181015186830182015201610cdd565b81811115610d0b576000602083870101525b50601f01601f19169290920160200192915050565b602081526000610d336020830184610cd3565b9392505050565b600060208284031215610d4c57600080fd5b81356001600160a01b0381168114610d3357600080fd5b803563ffffffff81168114610d7757600080fd5b919050565b803567ffffffffffffffff81168114610d7757600080fd5b600080600080600080600060e0888a031215610daf57600080fd5b87359650602088013567ffffffffffffffff80821115610dce57600080fd5b610dda8b838c01610bb7565b975060408a01359650610def60608b01610d63565b9550610dfd60808b01610d7c565b945060a08a0135915080821115610e1357600080fd5b610e1f8b838c01610bb7565b935060c08a0135915080821115610e3557600080fd5b50610e428a828b01610bb7565b91505092959891949750929550565b60008060008060808587031215610e6757600080fd5b84359350602085013567ffffffffffffffff80821115610e8657600080fd5b610e9288838901610bb7565b9450604087013591508115158214610ea957600080fd5b90925060608601359080821115610ebf57600080fd5b50610ecc87828801610bb7565b91505092959194509250565b60008060008060008060c08789031215610ef157600080fd5b86359550602087013567ffffffffffffffff80821115610f1057600080fd5b610f1c8a838b01610bb7565b965060408901359550610f3160608a01610d63565b9450610f3f60808a01610d7c565b935060a0890135915080821115610f5557600080fd5b50610f6289828a01610bb7565b9150509295509295509295565b606081526000610f826060830186610cd356","5b8460208401528281036040840152610f9a8185610cd3565b9695505050505050565b608081526000610fb76080830187610cd3565b8560208401528281036040840152610fcf8186610cd3565b915050821515606083015295945050505050565b600181811c90821680610ff757607f821691505b602082108114156110185763b95aa35560e01b600052602260045260246000fd5b50919050565b6020808252818101527f4f776e61626c653a2063616c6c6572206973206e6f7420746865206f776e6572604082015260600190565b87815260e06020820152600061106c60e0830189610cd3565b87604084015263ffffffff8716606084015267ffffffffffffffff8616608084015282810360a08401526110a08186610cd3565b905082810360c08401526110b48185610cd3565b9a9950505050505050505050565b6080815260006110d56080830187610cd3565b856020840152841515604084015282810360608401526110f58185610cd3565b979650505050505050565b60006020828403121561111257600080fd5b5051919050565b86815260c06020820152600061113260c0830188610cd3565b86604084015263ffffffff8616606084015267ffffffffffffffff8516608084015282810360a08401526111668185610cd3565b999850505050505050505056fea2646970667358221220ec39d6f57340a1527abbd45581a9b60f1290135dd5ba89363de4c6a0345a821a64736f6c634300080b0033"};

    public static final String SM_BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", SM_BINARY_ARRAY);

    public static final String[] ABI_ARRAY = {"[{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"messageId\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"uint32\",\"name\":\"sequence\",\"type\":\"uint32\"},{\"indexed\":false,\"internalType\":\"uint64\",\"name\":\"nonce\",\"type\":\"uint64\"},{\"indexed\":false,\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"},{\"indexed\":false,\"internalType\":\"string\",\"name\":\"errorMsg\",\"type\":\"string\"}],\"name\":\"AckOnError\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"messageId\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"uint32\",\"name\":\"sequence\",\"type\":\"uint32\"},{\"indexed\":false,\"internalType\":\"uint64\",\"name\":\"nonce\",\"type\":\"uint64\"},{\"indexed\":false,\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"AckOnSuccess\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"internalType\":\"address\",\"name\":\"previousOwner\",\"type\":\"address\"},{\"indexed\":true,\"internalType\":\"address\",\"name\":\"newOwner\",\"type\":\"address\"}],\"name\":\"OwnershipTransferred\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"internalType\":\"string\",\"name\":\"senderDomain\",\"type\":\"string\"},{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"author\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"},{\"indexed\":false,\"internalType\":\"bool\",\"name\":\"isOrdered\",\"type\":\"bool\"}],\"name\":\"recvCrosschainMsg\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"indexed\":false,\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"indexed\":false,\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"},{\"indexed\":false,\"internalType\":\"bool\",\"name\":\"isOrdered\",\"type\":\"bool\"}],\"name\":\"sendCrosschainMsg\",\"type\":\"event\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"messageId\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"uint32\",\"name\":\"sequence\",\"type\":\"uint32\"},{\"internalType\":\"uint64\",\"name\":\"nonce\",\"type\":\"uint64\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"},{\"internalType\":\"string\",\"name\":\"errorMsg\",\"type\":\"string\"}],\"name\":\"ackOnError\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"messageId\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"uint32\",\"name\":\"sequence\",\"type\":\"uint32\"},{\"internalType\":\"uint64\",\"name\":\"nonce\",\"type\":\"uint64\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"ackOnSuccess\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"getLastMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"getLastUnorderedMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"last_msg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"last_uo_msg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"latest_msg_error\",\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"latest_msg_id_ack_error\",\"outputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"latest_msg_id_ack_success\",\"outputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"latest_msg_id_sent_order\",\"outputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"latest_msg_id_sent_unorder\",\"outputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"owner\",\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"senderDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"author\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"recvMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"},{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"name\":\"recvMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"senderDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"author\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"recvUnorderedMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"renounceOwnership\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"name\":\"sdpAddress\",\"outputs\":[{\"internalType\":\"address\",\"name\":\"\",\"type\":\"address\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"sendMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"\",\"type\":\"bytes32\"},{\"internalType\":\"uint256\",\"name\":\"\",\"type\":\"uint256\"}],\"name\":\"sendMsg\",\"outputs\":[{\"internalType\":\"bytes\",\"name\":\"\",\"type\":\"bytes\"}],\"stateMutability\":\"view\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"string\",\"name\":\"receiverDomain\",\"type\":\"string\"},{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"bytes\",\"name\":\"message\",\"type\":\"bytes\"}],\"name\":\"sendUnorderedMessage\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"domain\",\"type\":\"string\"},{\"internalType\":\"bool\",\"name\":\"atomic\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"_msg\",\"type\":\"bytes\"}],\"name\":\"sendUnorderedV2\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"domain\",\"type\":\"string\"},{\"internalType\":\"bool\",\"name\":\"atomic\",\"type\":\"bool\"},{\"internalType\":\"bytes\",\"name\":\"_msg\",\"type\":\"bytes\"}],\"name\":\"sendV2\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"address\",\"name\":\"protocolAddress\",\"type\":\"address\"}],\"name\":\"setProtocol\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"address\",\"name\":\"newOwner\",\"type\":\"address\"}],\"name\":\"transferOwnership\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]"};

    public static final String ABI = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", ABI_ARRAY);

    public static final String FUNC_ACKONERROR = "ackOnError";

    public static final String FUNC_ACKONSUCCESS = "ackOnSuccess";

    public static final String FUNC_GETLASTMSG = "getLastMsg";

    public static final String FUNC_GETLASTUNORDEREDMSG = "getLastUnorderedMsg";

    public static final String FUNC_LAST_MSG = "last_msg";

    public static final String FUNC_LAST_UO_MSG = "last_uo_msg";

    public static final String FUNC_LATEST_MSG_ERROR = "latest_msg_error";

    public static final String FUNC_LATEST_MSG_ID_ACK_ERROR = "latest_msg_id_ack_error";

    public static final String FUNC_LATEST_MSG_ID_ACK_SUCCESS = "latest_msg_id_ack_success";

    public static final String FUNC_LATEST_MSG_ID_SENT_ORDER = "latest_msg_id_sent_order";

    public static final String FUNC_LATEST_MSG_ID_SENT_UNORDER = "latest_msg_id_sent_unorder";

    public static final String FUNC_OWNER = "owner";

    public static final String FUNC_RECVMESSAGE = "recvMessage";

    public static final String FUNC_RECVMSG = "recvMsg";

    public static final String FUNC_RECVUNORDEREDMESSAGE = "recvUnorderedMessage";

    public static final String FUNC_RENOUNCEOWNERSHIP = "renounceOwnership";

    public static final String FUNC_SDPADDRESS = "sdpAddress";

    public static final String FUNC_SENDMESSAGE = "sendMessage";

    public static final String FUNC_SENDMSG = "sendMsg";

    public static final String FUNC_SENDUNORDEREDMESSAGE = "sendUnorderedMessage";

    public static final String FUNC_SENDUNORDEREDV2 = "sendUnorderedV2";

    public static final String FUNC_SENDV2 = "sendV2";

    public static final String FUNC_SETPROTOCOL = "setProtocol";

    public static final String FUNC_TRANSFEROWNERSHIP = "transferOwnership";

    public static final Event ACKONERROR_EVENT = new Event("AckOnError", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint64>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<Utf8String>() {}));
    ;

    public static final Event ACKONSUCCESS_EVENT = new Event("AckOnSuccess", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint64>() {}, new TypeReference<DynamicBytes>() {}));
    ;

    public static final Event OWNERSHIPTRANSFERRED_EVENT = new Event("OwnershipTransferred", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}));
    ;

    public static final Event RECVCROSSCHAINMSG_EVENT = new Event("recvCrosschainMsg", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<Bool>() {}));
    ;

    public static final Event SENDCROSSCHAINMSG_EVENT = new Event("sendCrosschainMsg", 
            Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<Bool>() {}));
    ;

    protected AppContract(String contractAddress, Client client, CryptoKeyPair credential) {
        super(getBinary(client.getCryptoSuite()), contractAddress, client, credential);
    }

    public static String getBinary(CryptoSuite cryptoSuite) {
        return (cryptoSuite.getCryptoTypeConfig() == CryptoType.ECDSA_TYPE ? BINARY : SM_BINARY);
    }

    public static String getABI() {
        return ABI;
    }

    public List<AckOnErrorEventResponse> getAckOnErrorEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ACKONERROR_EVENT, transactionReceipt);
        ArrayList<AckOnErrorEventResponse> responses = new ArrayList<AckOnErrorEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AckOnErrorEventResponse typedResponse = new AckOnErrorEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.messageId = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.receiverDomain = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.receiver = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.sequence = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
            typedResponse.message = (byte[]) eventValues.getNonIndexedValues().get(5).getValue();
            typedResponse.errorMsg = (String) eventValues.getNonIndexedValues().get(6).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeAckOnErrorEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(ACKONERROR_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeAckOnErrorEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(ACKONERROR_EVENT);
        subscribeEvent(topic0,callback);
    }

    public List<AckOnSuccessEventResponse> getAckOnSuccessEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(ACKONSUCCESS_EVENT, transactionReceipt);
        ArrayList<AckOnSuccessEventResponse> responses = new ArrayList<AckOnSuccessEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            AckOnSuccessEventResponse typedResponse = new AckOnSuccessEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.messageId = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.receiverDomain = (String) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.receiver = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.sequence = (BigInteger) eventValues.getNonIndexedValues().get(3).getValue();
            typedResponse.nonce = (BigInteger) eventValues.getNonIndexedValues().get(4).getValue();
            typedResponse.message = (byte[]) eventValues.getNonIndexedValues().get(5).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeAckOnSuccessEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(ACKONSUCCESS_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeAckOnSuccessEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(ACKONSUCCESS_EVENT);
        subscribeEvent(topic0,callback);
    }

    public List<OwnershipTransferredEventResponse> getOwnershipTransferredEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(OWNERSHIPTRANSFERRED_EVENT, transactionReceipt);
        ArrayList<OwnershipTransferredEventResponse> responses = new ArrayList<OwnershipTransferredEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            OwnershipTransferredEventResponse typedResponse = new OwnershipTransferredEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.previousOwner = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.newOwner = (String) eventValues.getIndexedValues().get(1).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeOwnershipTransferredEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(OWNERSHIPTRANSFERRED_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeOwnershipTransferredEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(OWNERSHIPTRANSFERRED_EVENT);
        subscribeEvent(topic0,callback);
    }

    public List<RecvCrosschainMsgEventResponse> getRecvCrosschainMsgEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(RECVCROSSCHAINMSG_EVENT, transactionReceipt);
        ArrayList<RecvCrosschainMsgEventResponse> responses = new ArrayList<RecvCrosschainMsgEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            RecvCrosschainMsgEventResponse typedResponse = new RecvCrosschainMsgEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.senderDomain = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.author = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.message = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.isOrdered = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeRecvCrosschainMsgEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(RECVCROSSCHAINMSG_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeRecvCrosschainMsgEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(RECVCROSSCHAINMSG_EVENT);
        subscribeEvent(topic0,callback);
    }

    public List<SendCrosschainMsgEventResponse> getSendCrosschainMsgEvents(
            TransactionReceipt transactionReceipt) {
        List<Contract.EventValuesWithLog> valueList = extractEventParametersWithLog(SENDCROSSCHAINMSG_EVENT, transactionReceipt);
        ArrayList<SendCrosschainMsgEventResponse> responses = new ArrayList<SendCrosschainMsgEventResponse>(valueList.size());
        for (Contract.EventValuesWithLog eventValues : valueList) {
            SendCrosschainMsgEventResponse typedResponse = new SendCrosschainMsgEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.receiverDomain = (String) eventValues.getNonIndexedValues().get(0).getValue();
            typedResponse.receiver = (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
            typedResponse.message = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
            typedResponse.isOrdered = (Boolean) eventValues.getNonIndexedValues().get(3).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public void subscribeSendCrosschainMsgEvent(BigInteger fromBlock, BigInteger toBlock,
            List<String> otherTopics, EventSubCallback callback) {
        String topic0 = eventEncoder.encode(SENDCROSSCHAINMSG_EVENT);
        subscribeEvent(topic0,otherTopics,fromBlock,toBlock,callback);
    }

    public void subscribeSendCrosschainMsgEvent(EventSubCallback callback) {
        String topic0 = eventEncoder.encode(SENDCROSSCHAINMSG_EVENT);
        subscribeEvent(topic0,callback);
    }

    public TransactionReceipt ackOnError(byte[] messageId, String receiverDomain, byte[] receiver,
            BigInteger sequence, BigInteger nonce, byte[] message, String errorMsg) {
        final Function function = new Function(
                FUNC_ACKONERROR, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(errorMsg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodAckOnErrorRawFunction(byte[] messageId, String receiverDomain,
            byte[] receiver, BigInteger sequence, BigInteger nonce, byte[] message, String errorMsg)
            throws ContractException {
        final Function function = new Function(FUNC_ACKONERROR, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(errorMsg)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForAckOnError(byte[] messageId, String receiverDomain,
            byte[] receiver, BigInteger sequence, BigInteger nonce, byte[] message,
            String errorMsg) {
        final Function function = new Function(
                FUNC_ACKONERROR, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(errorMsg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String ackOnError(byte[] messageId, String receiverDomain, byte[] receiver,
            BigInteger sequence, BigInteger nonce, byte[] message, String errorMsg,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_ACKONERROR, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(errorMsg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple7<byte[], String, byte[], BigInteger, BigInteger, byte[], String> getAckOnErrorInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_ACKONERROR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint64>() {}, new TypeReference<DynamicBytes>() {}, new TypeReference<Utf8String>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple7<byte[], String, byte[], BigInteger, BigInteger, byte[], String>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue(), 
                (BigInteger) results.get(3).getValue(), 
                (BigInteger) results.get(4).getValue(), 
                (byte[]) results.get(5).getValue(), 
                (String) results.get(6).getValue()
                );
    }

    public TransactionReceipt ackOnSuccess(byte[] messageId, String receiverDomain, byte[] receiver,
            BigInteger sequence, BigInteger nonce, byte[] message) {
        final Function function = new Function(
                FUNC_ACKONSUCCESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodAckOnSuccessRawFunction(byte[] messageId, String receiverDomain,
            byte[] receiver, BigInteger sequence, BigInteger nonce, byte[] message) throws
            ContractException {
        final Function function = new Function(FUNC_ACKONSUCCESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForAckOnSuccess(byte[] messageId, String receiverDomain,
            byte[] receiver, BigInteger sequence, BigInteger nonce, byte[] message) {
        final Function function = new Function(
                FUNC_ACKONSUCCESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String ackOnSuccess(byte[] messageId, String receiverDomain, byte[] receiver,
            BigInteger sequence, BigInteger nonce, byte[] message, TransactionCallback callback) {
        final Function function = new Function(
                FUNC_ACKONSUCCESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(messageId), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint32(sequence), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint64(nonce), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple6<byte[], String, byte[], BigInteger, BigInteger, byte[]> getAckOnSuccessInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_ACKONSUCCESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<Uint32>() {}, new TypeReference<Uint64>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple6<byte[], String, byte[], BigInteger, BigInteger, byte[]>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue(), 
                (BigInteger) results.get(3).getValue(), 
                (BigInteger) results.get(4).getValue(), 
                (byte[]) results.get(5).getValue()
                );
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

    public byte[] last_msg() throws ContractException {
        final Function function = new Function(FUNC_LAST_MSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLast_msgRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LAST_MSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public byte[] last_uo_msg() throws ContractException {
        final Function function = new Function(FUNC_LAST_UO_MSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLast_uo_msgRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LAST_UO_MSG, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public String latest_msg_error() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ERROR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return executeCallWithSingleValueReturn(function, String.class);
    }

    public Function getMethodLatest_msg_errorRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ERROR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}));
        return function;
    }

    public byte[] latest_msg_id_ack_error() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_ACK_ERROR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLatest_msg_id_ack_errorRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_ACK_ERROR, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return function;
    }

    public byte[] latest_msg_id_ack_success() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_ACK_SUCCESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLatest_msg_id_ack_successRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_ACK_SUCCESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return function;
    }

    public byte[] latest_msg_id_sent_order() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_SENT_ORDER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLatest_msg_id_sent_orderRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_SENT_ORDER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return function;
    }

    public byte[] latest_msg_id_sent_unorder() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_SENT_UNORDER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodLatest_msg_id_sent_unorderRawFunction() throws ContractException {
        final Function function = new Function(FUNC_LATEST_MSG_ID_SENT_UNORDER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
        return function;
    }

    public String owner() throws ContractException {
        final Function function = new Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeCallWithSingleValueReturn(function, String.class);
    }

    public Function getMethodOwnerRawFunction() throws ContractException {
        final Function function = new Function(FUNC_OWNER, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return function;
    }

    public TransactionReceipt recvMessage(String senderDomain, byte[] author, byte[] message) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodRecvMessageRawFunction(String senderDomain, byte[] author,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForRecvMessage(String senderDomain, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String recvMessage(String senderDomain, byte[] author, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_RECVMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
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

    public byte[] recvMsg(byte[] param0, BigInteger param1) throws ContractException {
        final Function function = new Function(FUNC_RECVMSG, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(param0), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodRecvMsgRawFunction(byte[] param0, BigInteger param1) throws
            ContractException {
        final Function function = new Function(FUNC_RECVMSG, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(param0), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public TransactionReceipt recvUnorderedMessage(String senderDomain, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodRecvUnorderedMessageRawFunction(String senderDomain, byte[] author,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForRecvUnorderedMessage(String senderDomain, byte[] author,
            byte[] message) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(author), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String recvUnorderedMessage(String senderDomain, byte[] author, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_RECVUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(senderDomain), 
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

    public TransactionReceipt renounceOwnership() {
        final Function function = new Function(
                FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodRenounceOwnershipRawFunction() throws ContractException {
        final Function function = new Function(FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForRenounceOwnership() {
        final Function function = new Function(
                FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String renounceOwnership(TransactionCallback callback) {
        final Function function = new Function(
                FUNC_RENOUNCEOWNERSHIP, 
                Arrays.<Type>asList(), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public String sdpAddress() throws ContractException {
        final Function function = new Function(FUNC_SDPADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return executeCallWithSingleValueReturn(function, String.class);
    }

    public Function getMethodSdpAddressRawFunction() throws ContractException {
        final Function function = new Function(FUNC_SDPADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        return function;
    }

    public TransactionReceipt sendMessage(String receiverDomain, byte[] receiver, byte[] message) {
        final Function function = new Function(
                FUNC_SENDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendMessageRawFunction(String receiverDomain, byte[] receiver,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_SENDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSendMessage(String receiverDomain, byte[] receiver,
            byte[] message) {
        final Function function = new Function(
                FUNC_SENDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String sendMessage(String receiverDomain, byte[] receiver, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SENDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<String, byte[], byte[]> getSendMessageInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SENDMESSAGE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<String, byte[], byte[]>(

                (String) results.get(0).getValue(), 
                (byte[]) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public byte[] sendMsg(byte[] param0, BigInteger param1) throws ContractException {
        final Function function = new Function(FUNC_SENDMSG, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(param0), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return executeCallWithSingleValueReturn(function, byte[].class);
    }

    public Function getMethodSendMsgRawFunction(byte[] param0, BigInteger param1) throws
            ContractException {
        final Function function = new Function(FUNC_SENDMSG, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(param0), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Uint256(param1)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
        return function;
    }

    public TransactionReceipt sendUnorderedMessage(String receiverDomain, byte[] receiver,
            byte[] message) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendUnorderedMessageRawFunction(String receiverDomain, byte[] receiver,
            byte[] message) throws ContractException {
        final Function function = new Function(FUNC_SENDUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSendUnorderedMessage(String receiverDomain,
            byte[] receiver, byte[] message) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String sendUnorderedMessage(String receiverDomain, byte[] receiver, byte[] message,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDMESSAGE, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(receiverDomain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(message)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<String, byte[], byte[]> getSendUnorderedMessageInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SENDUNORDEREDMESSAGE, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {}, new TypeReference<Bytes32>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<String, byte[], byte[]>(

                (String) results.get(0).getValue(), 
                (byte[]) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public TransactionReceipt sendUnorderedV2(byte[] receiver, String domain, Boolean atomic,
            byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendUnorderedV2RawFunction(byte[] receiver, String domain,
            Boolean atomic, byte[] _msg) throws ContractException {
        final Function function = new Function(FUNC_SENDUNORDEREDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSendUnorderedV2(byte[] receiver, String domain,
            Boolean atomic, byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String sendUnorderedV2(byte[] receiver, String domain, Boolean atomic, byte[] _msg,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SENDUNORDEREDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple4<byte[], String, Boolean, byte[]> getSendUnorderedV2Input(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SENDUNORDEREDV2, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bool>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple4<byte[], String, Boolean, byte[]>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (Boolean) results.get(2).getValue(), 
                (byte[]) results.get(3).getValue()
                );
    }

    public TransactionReceipt sendV2(byte[] receiver, String domain, Boolean atomic, byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendV2RawFunction(byte[] receiver, String domain, Boolean atomic,
            byte[] _msg) throws ContractException {
        final Function function = new Function(FUNC_SENDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSendV2(byte[] receiver, String domain, Boolean atomic,
            byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String sendV2(byte[] receiver, String domain, Boolean atomic, byte[] _msg,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SENDV2, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Bool(atomic), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple4<byte[], String, Boolean, byte[]> getSendV2Input(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SENDV2, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<Bool>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple4<byte[], String, Boolean, byte[]>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (Boolean) results.get(2).getValue(), 
                (byte[]) results.get(3).getValue()
                );
    }

    public TransactionReceipt setProtocol(String protocolAddress) {
        final Function function = new Function(
                FUNC_SETPROTOCOL, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(protocolAddress)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSetProtocolRawFunction(String protocolAddress) throws
            ContractException {
        final Function function = new Function(FUNC_SETPROTOCOL, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(protocolAddress)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSetProtocol(String protocolAddress) {
        final Function function = new Function(
                FUNC_SETPROTOCOL, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(protocolAddress)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String setProtocol(String protocolAddress, TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SETPROTOCOL, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(protocolAddress)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple1<String> getSetProtocolInput(TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SETPROTOCOL, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple1<String>(

                (String) results.get(0).getValue()
                );
    }

    public TransactionReceipt transferOwnership(String newOwner) {
        final Function function = new Function(
                FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(newOwner)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodTransferOwnershipRawFunction(String newOwner) throws
            ContractException {
        final Function function = new Function(FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(newOwner)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForTransferOwnership(String newOwner) {
        final Function function = new Function(
                FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(newOwner)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String transferOwnership(String newOwner, TransactionCallback callback) {
        final Function function = new Function(
                FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(newOwner)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple1<String> getTransferOwnershipInput(TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_TRANSFEROWNERSHIP, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple1<String>(

                (String) results.get(0).getValue()
                );
    }

    public static AppContract load(String contractAddress, Client client,
            CryptoKeyPair credential) {
        return new AppContract(contractAddress, client, credential);
    }

    public static AppContract deploy(Client client, CryptoKeyPair credential) throws
            ContractException {
        return deploy(AppContract.class, client, credential, getBinary(client.getCryptoSuite()), getABI(), null, null);
    }

    public static class AckOnErrorEventResponse {
        public TransactionReceipt.Logs log;

        public byte[] messageId;

        public String receiverDomain;

        public byte[] receiver;

        public BigInteger sequence;

        public BigInteger nonce;

        public byte[] message;

        public String errorMsg;
    }

    public static class AckOnSuccessEventResponse {
        public TransactionReceipt.Logs log;

        public byte[] messageId;

        public String receiverDomain;

        public byte[] receiver;

        public BigInteger sequence;

        public BigInteger nonce;

        public byte[] message;
    }

    public static class OwnershipTransferredEventResponse {
        public TransactionReceipt.Logs log;

        public String previousOwner;

        public String newOwner;
    }

    public static class RecvCrosschainMsgEventResponse {
        public TransactionReceipt.Logs log;

        public String senderDomain;

        public byte[] author;

        public byte[] message;

        public Boolean isOrdered;
    }

    public static class SendCrosschainMsgEventResponse {
        public TransactionReceipt.Logs log;

        public String receiverDomain;

        public byte[] receiver;

        public byte[] message;

        public Boolean isOrdered;
    }
}
