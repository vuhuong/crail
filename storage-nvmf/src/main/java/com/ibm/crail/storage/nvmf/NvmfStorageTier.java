/*
 * Crail: A Multi-tiered Distributed Direct Access File System
 *
 * Author:
 * Jonas Pfefferle <jpf@zurich.ibm.com>
 *
 * Copyright (C) 2016, IBM Corporation
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
 *
 */

package com.ibm.crail.storage.nvmf;

import com.ibm.crail.conf.CrailConfiguration;
import com.ibm.crail.metadata.DataNodeInfo;
import com.ibm.crail.storage.StorageEndpoint;
import com.ibm.crail.utils.CrailUtils;
import com.ibm.crail.storage.StorageTier;
import com.ibm.crail.storage.nvmf.client.NvmfStorageEndpoint;
import com.ibm.disni.nvmef.NvmeEndpointGroup;
import com.ibm.disni.nvmef.spdk.*;

import org.apache.commons.cli.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;

public class NvmfStorageTier extends StorageTier {

	private static final Logger LOG = CrailUtils.getLogger();
	private static NvmeEndpointGroup clientGroup;
	private boolean initialized = false;

	public void init(CrailConfiguration crailConfiguration, String[] args) throws IOException {

		if (initialized) {
			throw new IOException("NvmfStorageTier already initialized");
		}
		initialized = true;

		NvmfStorageConstants.updateConstants(crailConfiguration);

		if (args != null) {
			Options options = new Options();
			Option bindIp = Option.builder("a").desc("ip address to bind to").hasArg().build();
			Option port = Option.builder("p").desc("port to bind to").hasArg().type(Number.class).build();
			Option pcieAddress = Option.builder("s").desc("PCIe address of NVMe device").hasArg().build();
			options.addOption(bindIp);
			options.addOption(port);
			options.addOption(pcieAddress);
			CommandLineParser parser = new DefaultParser();
			HelpFormatter formatter = new HelpFormatter();
			CommandLine line = null;
			try {
				line = parser.parse(options, Arrays.copyOfRange(args, 0, args.length));
				if (line.hasOption(port.getOpt())) {
					NvmfStorageConstants.PORT = ((Number) line.getParsedOptionValue(port.getOpt())).intValue();
				}
			} catch (ParseException e) {
				System.err.println(e.getMessage());
				formatter.printHelp("NVMe storage tier", options);
				System.exit(-1);
			}
			if (line.hasOption(bindIp.getOpt())) {
				NvmfStorageConstants.IP_ADDR = InetAddress.getByName(line.getOptionValue(bindIp.getOpt()));
			}
			if (line.hasOption(pcieAddress.getOpt())) {
				NvmfStorageConstants.PCIE_ADDR = line.getOptionValue(pcieAddress.getOpt());
			}
		}

		NvmfStorageConstants.verify();
	}

	public void printConf(Logger logger) {
		NvmfStorageConstants.printConf(logger);
	}

	public static NvmeEndpointGroup getEndpointGroup() {
		if (clientGroup == null) {
			clientGroup = new NvmeEndpointGroup(new NvmeTransportType[]{NvmeTransportType.RDMA},
					NvmfStorageConstants.HUGEDIR,
					NvmfStorageConstants.CLIENT_MEMPOOL);
		}
		return clientGroup;
	}

	public synchronized StorageEndpoint createEndpoint(DataNodeInfo info) throws IOException {
		return new NvmfStorageEndpoint(getEndpointGroup(), CrailUtils.datanodeInfo2SocketAddr(info));
	}

	public NvmfStorageServer launchServer() throws Exception {
		LOG.info("initalizing NVMf datanode");
		NvmfStorageServer storageServer = new NvmfStorageServer();
		Thread server = new Thread(storageServer);
		server.start();
		return storageServer;
	}

	public void close() throws Exception {
	}
}
