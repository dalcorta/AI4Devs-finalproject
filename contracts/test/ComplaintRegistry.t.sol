// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {IAccessControl} from "@openzeppelin/contracts/access/IAccessControl.sol";
import {ComplaintRegistry} from "../src/ComplaintRegistry.sol";

contract ComplaintRegistryTest is Test {
    ComplaintRegistry registry;

    address admin = address(0xA11CE);
    address relayer = address(0xBEEF);
    address officer = address(0xCAFE);
    address stranger = address(0xD00D);

    bytes32 constant CONTENT_HASH = keccak256("contenido-publico-de-prueba");
    string constant IPFS_CID = "bafyExampleCid";
    uint64 constant COMPANY_CUIT = 30712345678;

    function setUp() public {
        registry = new ComplaintRegistry(admin);

        vm.startPrank(admin);
        registry.grantRole(registry.RELAYER_ROLE(), relayer);
        registry.grantRole(registry.OFFICER_ROLE(), officer);
        vm.stopPrank();
    }

    function test_fileComplaint_emitsEventAndStoresData() public {
        vm.expectEmit(true, true, false, true, address(registry));
        emit ComplaintRegistry.ComplaintFiled(1, COMPANY_CUIT, CONTENT_HASH, IPFS_CID);

        vm.prank(relayer);
        uint256 id = registry.fileComplaint(COMPANY_CUIT, CONTENT_HASH, IPFS_CID);

        assertEq(id, 1);

        ComplaintRegistry.Complaint memory complaint = registry.getComplaint(id);
        assertEq(complaint.contentHash, CONTENT_HASH);
        assertEq(complaint.ipfsCid, IPFS_CID);
        assertEq(complaint.companyCuit, COMPANY_CUIT);
        assertEq(uint8(complaint.status), uint8(ComplaintRegistry.Status.REGISTRADA));
    }

    function test_fileComplaint_revertsOnDuplicateContentHash() public {
        uint256 id = _fileSampleComplaint();

        vm.prank(relayer);
        vm.expectRevert(abi.encodeWithSelector(ComplaintRegistry.ComplaintAlreadyFiled.selector, CONTENT_HASH, id));
        registry.fileComplaint(COMPANY_CUIT, CONTENT_HASH, "otroCid");
    }

    function test_complaintIdByHash_returnsIdOrZero() public {
        assertEq(registry.complaintIdByHash(CONTENT_HASH), 0);

        uint256 id = _fileSampleComplaint();

        assertEq(registry.complaintIdByHash(CONTENT_HASH), id);
    }

    function test_fileComplaint_revertsWithoutRelayerRole() public {
        bytes32 relayerRole = registry.RELAYER_ROLE();
        vm.prank(stranger);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, stranger, relayerRole)
        );
        registry.fileComplaint(COMPANY_CUIT, CONTENT_HASH, IPFS_CID);
    }

    function test_changeStatus_validTransitionSucceeds() public {
        uint256 id = _fileSampleComplaint();

        vm.expectEmit(true, false, false, true, address(registry));
        emit ComplaintRegistry.StatusChanged(
            id, ComplaintRegistry.Status.REGISTRADA, ComplaintRegistry.Status.EN_REVISION, bytes32(0)
        );

        vm.prank(officer);
        registry.changeStatus(id, ComplaintRegistry.Status.EN_REVISION, bytes32(0));

        ComplaintRegistry.Complaint memory complaint = registry.getComplaint(id);
        assertEq(uint8(complaint.status), uint8(ComplaintRegistry.Status.EN_REVISION));
    }

    function test_changeStatus_revertsWithoutOfficerRole() public {
        uint256 id = _fileSampleComplaint();
        bytes32 officerRole = registry.OFFICER_ROLE();

        vm.prank(stranger);
        vm.expectRevert(
            abi.encodeWithSelector(IAccessControl.AccessControlUnauthorizedAccount.selector, stranger, officerRole)
        );
        registry.changeStatus(id, ComplaintRegistry.Status.EN_REVISION, bytes32(0));
    }

    function test_changeStatus_revertsOnInvalidTransition() public {
        uint256 id = _fileSampleComplaint();

        // REGISTRADA -> DERIVADA salta EN_REVISION: inválida.
        vm.prank(officer);
        vm.expectRevert(
            abi.encodeWithSelector(
                ComplaintRegistry.InvalidTransition.selector,
                ComplaintRegistry.Status.REGISTRADA,
                ComplaintRegistry.Status.DERIVADA
            )
        );
        registry.changeStatus(id, ComplaintRegistry.Status.DERIVADA, bytes32(0));
    }

    function test_changeStatus_revertsOnUnknownComplaint() public {
        vm.prank(officer);
        vm.expectRevert(abi.encodeWithSelector(ComplaintRegistry.ComplaintNotFound.selector, 999));
        registry.changeStatus(999, ComplaintRegistry.Status.EN_REVISION, bytes32(0));
    }

    function test_fullHappyPathToResuelta() public {
        uint256 id = _fileSampleComplaint();

        vm.startPrank(officer);
        registry.changeStatus(id, ComplaintRegistry.Status.EN_REVISION, bytes32(0));
        registry.changeStatus(id, ComplaintRegistry.Status.DERIVADA, bytes32(0));
        registry.changeStatus(id, ComplaintRegistry.Status.RESUELTA, keccak256("resuelto a favor del consumidor"));
        vm.stopPrank();

        ComplaintRegistry.Complaint memory complaint = registry.getComplaint(id);
        assertEq(uint8(complaint.status), uint8(ComplaintRegistry.Status.RESUELTA));
    }

    function test_changeStatus_revertsOnTerminalState() public {
        uint256 id = _fileSampleComplaint();

        vm.startPrank(officer);
        registry.changeStatus(id, ComplaintRegistry.Status.EN_REVISION, bytes32(0));
        registry.changeStatus(id, ComplaintRegistry.Status.DERIVADA, bytes32(0));
        registry.changeStatus(id, ComplaintRegistry.Status.RESUELTA, bytes32(0));

        vm.expectRevert(
            abi.encodeWithSelector(
                ComplaintRegistry.InvalidTransition.selector,
                ComplaintRegistry.Status.RESUELTA,
                ComplaintRegistry.Status.DESESTIMADA
            )
        );
        registry.changeStatus(id, ComplaintRegistry.Status.DESESTIMADA, bytes32(0));
        vm.stopPrank();
    }

    function _fileSampleComplaint() private returns (uint256 id) {
        vm.prank(relayer);
        id = registry.fileComplaint(COMPANY_CUIT, CONTENT_HASH, IPFS_CID);
    }
}
