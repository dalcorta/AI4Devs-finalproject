// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console} from "forge-std/Script.sol";
import {ComplaintRegistry} from "../src/ComplaintRegistry.sol";

/// @notice Despliega ComplaintRegistry y, si están presentes, otorga RELAYER_ROLE
/// y OFFICER_ROLE a las direcciones indicadas por variables de entorno.
/// Variables esperadas:
///   PRIVATE_KEY   - clave del deployer (también admin por defecto)
///   RELAYER_ADDR  - dirección del relayer del backend (opcional)
///   OFFICER_ADDR  - dirección del funcionario/servicio de panel (opcional)
contract Deploy is Script {
    function run() external returns (ComplaintRegistry registry) {
        uint256 deployerPrivateKey = vm.envUint("PRIVATE_KEY");
        address admin = vm.addr(deployerPrivateKey);

        vm.startBroadcast(deployerPrivateKey);

        registry = new ComplaintRegistry(admin);
        console.log("ComplaintRegistry desplegado en:", address(registry));

        address relayerAddr = vm.envOr("RELAYER_ADDR", address(0));
        if (relayerAddr != address(0)) {
            registry.grantRole(registry.RELAYER_ROLE(), relayerAddr);
            console.log("RELAYER_ROLE otorgado a:", relayerAddr);
        }

        address officerAddr = vm.envOr("OFFICER_ADDR", address(0));
        if (officerAddr != address(0)) {
            registry.grantRole(registry.OFFICER_ROLE(), officerAddr);
            console.log("OFFICER_ROLE otorgado a:", officerAddr);
        }

        vm.stopBroadcast();
    }
}
