<div align="center">
  <img alt="am logo" src="https://gw.alipayobjects.com/zos/bmw-prod/3ee4adc7-1960-4dbf-982e-522ac135a0c0.svg" width="250" >
  <h1 align="center">Simple Ethereum Plugin</h1>
  <p align="center">
    <a href="http://makeapullrequest.com">
      <img alt="pull requests welcome badge" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat">
    </a>
  </p>
</div>

# Build

1. Run the following command to generate contracts abi code:

```
mvn web3j:generate-sources
```

2. Before compiling the plugin, you can modify the type and id of the plugin by modifying 
the `products`  and `pluginId`  attribute values in `EthereumBBCService.java` .
In the current code, the product is `simple-ethereum` , and the pluginId is `plugin-simple-ethereum`.

```java
@BBCService(products = "simple-ethereum", pluginId = "plugin-simple-ethereum")
```

3. Then execute the compile command in the plugin project directory 
to get the jar for use as a plugin
```agsl
mvn clean package -Dmaven.test.skip=true
```