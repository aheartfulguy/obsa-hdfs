package org.apache.hadoop.fs.obs;

import com.obs.services.ObsClient;
import com.obs.services.model.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.contract.ContractTestUtils;
import org.apache.hadoop.fs.obs.contract.OBSContract;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.apache.hadoop.fs.contract.ContractTestUtils.*;

public class ITestOBSBucketAcl {
    private OBSFileSystem fs;

    private int testBufferSize;

    private int modulus;

    private byte[] testBuffer;

    private static String testRootPath =
        OBSTestUtils.generateUniqueTestPath();

    String vAccountId = null;

    String permissionStr = null;

    @Rule
    public OBSTestRule testRule = new OBSTestRule();

    @BeforeClass
    public static void skipTestCheck() {
        Assume.assumeTrue(OBSContract.isContractTestEnabled());
    }

    @Before
    public void setUp() throws Exception {
        Configuration conf = OBSContract.getConfiguration(null);
        fs = OBSTestUtils.createTestFileSystem(conf);
        testBufferSize = fs.getConf().getInt("io.chunk.buffer.size", 128);
        modulus = fs.getConf().getInt("io.chunk.modulus.size", 128);
        testBuffer = new byte[testBufferSize];

        for (int i = 0; i < testBuffer.length; ++i) {
            testBuffer[i] = (byte) (i % modulus);
        }
        vAccountId = "domainiddomainiddomainiddo000001";
        permissionStr = String.valueOf(Permission.PERMISSION_FULL_CONTROL);
    }

    @After
    public void tearDown() throws Exception {
        if (fs != null) {
            fs.delete(new Path(testRootPath), true);
        }
    }

    private Path getTestPath() {
        return new Path(testRootPath + "/test-obs");
    }

    @Test
    // 上传文件，桶owner有FULL_CONTROL权限
    public void testUploadFileBucketOwnerPermission() throws IOException {
        if (!fs.isFsBucket()) {
            return;
        }
        verifyAppendAndAcl(1024 * 1024, 1024, 3);
    }

    @Test
    // 创建目录，桶owner有FULL_CONTROL权限
    public void testMkDirBucketOwnerPermission() throws IOException {
        if (!fs.isFsBucket()) {
            return;
        }
        Path dirPath = new Path(getTestPath(), "mkdir-zh");
        fs.mkdirs(dirPath);
        String key = dirPath.toString().substring(1);
        verifyAcl(key, vAccountId, permissionStr);
    }

    @Test
    // 从本地copy文件，桶owner有FULL_CONTROL权限
    public void testCopyFromLocalBucketOwnerPermission() throws IOException {
        String localFile = "local_file";
        new File(testRootPath, "." + localFile + ".crc").delete();
        OBSFSTestUtil.createLocalTestFile(localFile);
        Path localPath = new Path(localFile);
        Path dstPath = new Path(testRootPath + "/copylocal-huge");

        fs.copyFromLocalFile(false, true, localPath, dstPath);
        String key = dstPath.toString().substring(1);
        verifyAcl(key, vAccountId, permissionStr);
        OBSFSTestUtil.deleteLocalFile(localFile);
    }

    private void verifyAppendAndAcl(long fileSize, long appendSize,
        int appendTimes) throws IOException {
        long total = fileSize;
        Path objectPath = createAppendFile(fileSize);

        for (int i = 0; i < appendTimes; i++) {
            appendFile(objectPath, appendSize);
            total = total + appendSize;
        }
        verifyReceivedData(fs, objectPath, total, testBufferSize, modulus);
        String key = objectPath.toString().substring(1);
        verifyAcl(key, vAccountId, permissionStr);
    }

    private void verifyAcl(String key, String vAccountId,
        String permissionStr) {
        ObsClient client = fs.getObsClient();
        AccessControlList controlList = client.getObjectAcl(fs.getBucket(),
            key);
        Set<GrantAndPermission> grants = controlList.getGrants();
        boolean aclOk = false;
        for (GrantAndPermission grant : grants) {
            String accountId = grant.getGrantee().getIdentifier();
            String permission = grant.getPermission().getPermissionString();
            if (accountId.equals(vAccountId) && permission.equals(
                permissionStr)) {
                aclOk = true;
                break;
            }
        }
        assertTrue(aclOk);
    }

    private void appendFile(Path objectPath, long appendSize)
        throws IOException {
        OutputStream outputStream = fs.append(objectPath, 4096, null);
        writStream(outputStream, appendSize);
        assertPathExists(fs, "not created successful", objectPath);
    }

    private void writStream(OutputStream outputStream, long fileSize)
        throws IOException {
        long bytesWritten = 0L;
        Throwable var10 = null;
        long diff;
        try {
            while (bytesWritten < fileSize) {
                diff = fileSize - bytesWritten;
                if (diff < (long) testBuffer.length) {
                    outputStream.write(testBuffer, 0, (int) diff);
                    bytesWritten += diff;
                } else {
                    outputStream.write(testBuffer);
                    bytesWritten += (long) testBuffer.length;
                }
            }

            diff = bytesWritten;
        } catch (Throwable var21) {
            var10 = var21;
            throw var21;
        } finally {
            if (outputStream != null) {
                if (var10 != null) {
                    try {
                        outputStream.close();
                    } catch (Throwable var20) {
                        var10.addSuppressed(var20);
                    }
                } else {
                    outputStream.close();
                }
            }

        }
        assertEquals(fileSize, diff);
    }

    private Path createAppendFile(long fileSize) throws IOException {

        String objectName = UUID.randomUUID().toString();
        Path objectPath = new Path(getTestPath(), objectName);
        ContractTestUtils.NanoTimer timer = new ContractTestUtils.NanoTimer();

        OutputStream outputStream = creatAppendStream(objectPath);
        writStream(outputStream, fileSize);
        bandwidth(timer, fileSize);
        assertPathExists(fs, "not created successful", objectPath);
        return objectPath;
    }

    private OutputStream creatAppendStream(Path objectPath) throws IOException {
        EnumSet<CreateFlag> flags = EnumSet.noneOf(CreateFlag.class);
        flags.add(CreateFlag.APPEND);
        FsPermission permission = new FsPermission((short) 00644);
        return fs.create(objectPath, permission, flags,
            fs.getConf().getInt("io.file.buffer.size", 4096),
            fs.getDefaultReplication(objectPath),
            fs.getDefaultBlockSize(objectPath), (Progressable) null);

    }

}