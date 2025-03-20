/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.fs.obshadoop;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.FileSystemFactory;
import org.apache.flink.runtime.util.HadoopUtils;
import org.apache.hadoop.fs.obs.OBSFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;


public class FlinkOBSFsFactory implements FileSystemFactory {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkOBSFsFactory.class);

    private Configuration flinkConfig;

    private org.apache.hadoop.conf.Configuration hadoopConfig;

    @Override
    public void configure(Configuration config) {
        this.flinkConfig = config;
        this.hadoopConfig = null;
    }

    @Override
    public String getScheme() {
        return "obs";
    }

    @Override
    public FileSystem create(URI fsUri) throws IOException {
        // -- (1) get the loaded Hadoop config (or fall back to one loaded from the classpath)
        final org.apache.hadoop.conf.Configuration hadoopConfig;
        if (this.hadoopConfig != null) {
            hadoopConfig = this.hadoopConfig;
        } else if (flinkConfig != null) {
            hadoopConfig = HadoopUtils.getHadoopConfiguration(flinkConfig);
            this.hadoopConfig = hadoopConfig;
        } else {
            LOG.warn(
                "Hadoop configuration has not been explicitly initialized prior to loading a Hadoop file system."
                    + " Using configuration from the classpath.");

            hadoopConfig = new org.apache.hadoop.conf.Configuration();
        }
        hadoopConfig.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
        org.apache.hadoop.fs.FileSystem fs = new OBSFileSystem();
        fs.initialize(getInitURI(fsUri, hadoopConfig), hadoopConfig);

        return new FlinkOBSFileSystem(fs);
    }

    protected URI getInitURI(URI fsUri, org.apache.hadoop.conf.Configuration hadoopConfig) {
        final String scheme = fsUri.getScheme();
        final String authority = fsUri.getAuthority();

        if (scheme == null && authority == null) {
            fsUri = org.apache.hadoop.fs.FileSystem.getDefaultUri(hadoopConfig);
        } else if (scheme != null && authority == null) {
            URI defaultUri = org.apache.hadoop.fs.FileSystem.getDefaultUri(hadoopConfig);
            if (scheme.equals(defaultUri.getScheme()) && defaultUri.getAuthority() != null) {
                fsUri = defaultUri;
            }
        }

        return fsUri;
    }
}
