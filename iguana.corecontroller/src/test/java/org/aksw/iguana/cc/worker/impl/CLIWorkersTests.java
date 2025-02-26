package org.aksw.iguana.cc.worker.impl;

import org.apache.commons.lang.SystemUtils;
import org.aksw.iguana.cc.config.elements.Connection;
import org.aksw.iguana.cc.utils.FileUtils;
import org.aksw.iguana.commons.constants.COMMON;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Properties;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CLIWorkersTests {

    private File f;

    @Before
    public void createFile(){
        String file = UUID.randomUUID().toString();
        this.f = new File(file);
        ;
    }

    @After
    public void deleteFile(){
        f.delete();
    }

    @Test
    public void checkMultipleProcesses(){
        Connection con = new Connection();
        con.setEndpoint("src/test/resources/cli/echoinput.sh "+f.getAbsolutePath());
        MultipleCLIInputWorker worker = new MultipleCLIInputWorker("123/1/1", con, "src/test/resources/update/empty.nt", "init finished", "rows", "query fail", 2, null, null, null,null,  1);
        assertEquals(2, worker.processList.size());
        for(Process p : worker.processList){
            assertTrue(p.isAlive());
        }
        //should run normally
        assertEquals(0, worker.currentProcessId);
        worker.executeQuery("test", "1");
        assertEquals(0, worker.currentProcessId);
        worker.executeQuery("quit", "2");
        worker.executeQuery("test", "1");
        assertEquals(1, worker.currentProcessId);
        assertEquals(2, worker.processList.size());

        for(Process p : worker.processList){
            assertTrue(p.isAlive());
        }
        worker.executeQuery("quit", "2");
        worker.executeQuery("test", "1");
        assertEquals(0, worker.currentProcessId);
    }

    @Test
    public void checkFileInput() throws IOException {
        //check if file is created and used
        Connection con = new Connection();
        String dir = UUID.randomUUID().toString();
        con.setEndpoint("src/test/resources/cli/echoinput.sh "+f.getAbsolutePath());
        CLIInputFileWorker worker = new CLIInputFileWorker("123/1/1", con, "src/test/resources/update/empty.nt", "init finished", "rows", "query fail", 1, dir, null, null, null, null, 1);
        worker.executeQuery("test", "1");
        assertEquals("test", FileUtils.readFile(dir+File.separator+"tmpquery.sparql"));
        worker.executeQuery("SELECT whatever", "1");
        assertEquals("SELECT whatever", FileUtils.readFile(dir+File.separator+"tmpquery.sparql"));
        assertEquals("tmpquery.sparql\ntmpquery.sparql\n", FileUtils.readFile(f.getAbsolutePath()));

        org.apache.commons.io.FileUtils.deleteDirectory(new File(dir));
        worker.stopSending();

    }

    @Test
    public void checkInput() throws IOException {
        // check if connection stays
        Connection con = new Connection();

        con.setEndpoint("src/test/resources/cli/echoinput.sh "+f.getAbsolutePath());
        CLIInputWorker worker = new CLIInputWorker("123/1/1", con, "src/test/resources/update/empty.nt", "init finished", "rows", "query fail", null, null, null, null,  1);
        worker.executeQuery("test", "1");
        worker.executeQuery("SELECT whatever", "1");
        assertEquals("test\nSELECT whatever\n", FileUtils.readFile(f.getAbsolutePath()));
        Collection<Properties> succeededResults = worker.popQueryResults();
        assertEquals(2, succeededResults.size());
        Properties succ = succeededResults.iterator().next();
        assertEquals(COMMON.QUERY_SUCCESS, succ.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(3l, succ.get(COMMON.RECEIVE_DATA_SIZE));
        succ = succeededResults.iterator().next();
        assertEquals(COMMON.QUERY_SUCCESS, succ.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(3l, succ.get(COMMON.RECEIVE_DATA_SIZE));

        // check fail
        worker.executeQuery("fail", "2");
        assertEquals("test\nSELECT whatever\nfail\n", FileUtils.readFile(f.getAbsolutePath()));
        Collection<Properties> failedResults = worker.popQueryResults();
        assertEquals(1, failedResults.size());
        Properties fail = failedResults.iterator().next();
        assertEquals(COMMON.QUERY_UNKNOWN_EXCEPTION, fail.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(0l, fail.get(COMMON.RECEIVE_DATA_SIZE));
        worker.stopSending();


    }

    @Test
    public void checkPrefix() throws IOException {
        // check if connection stays
        Connection con = new Connection();

        con.setEndpoint("src/test/resources/cli/echoinput.sh "+f.getAbsolutePath());
        CLIInputPrefixWorker worker = new CLIInputPrefixWorker("123/1/1", con, "src/test/resources/update/empty.nt", "init finished", "rows", "query fail", 1, "prefix", "suffix", null, null, null, null,  1);
        worker.executeQuery("test", "1");
        worker.executeQuery("SELECT whatever", "1");
        assertEquals("prefix test suffix\nprefix SELECT whatever suffix\n", FileUtils.readFile(f.getAbsolutePath()));
        Collection<Properties> succeededResults = worker.popQueryResults();
        assertEquals(2, succeededResults.size());
        Properties succ = succeededResults.iterator().next();
        assertEquals(COMMON.QUERY_SUCCESS, succ.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(3l, succ.get(COMMON.RECEIVE_DATA_SIZE));
        succ = succeededResults.iterator().next();
        assertEquals(COMMON.QUERY_SUCCESS, succ.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(3l, succ.get(COMMON.RECEIVE_DATA_SIZE));

        // check fail
        worker.executeQuery("fail", "2");
        assertEquals("prefix test suffix\nprefix SELECT whatever suffix\nprefix fail suffix\n", FileUtils.readFile(f.getAbsolutePath()));
        Collection<Properties> failedResults = worker.popQueryResults();
        assertEquals(1, failedResults.size());
        Properties fail = failedResults.iterator().next();
        assertEquals(COMMON.QUERY_UNKNOWN_EXCEPTION, fail.get(COMMON.RECEIVE_DATA_SUCCESS));
        assertEquals(0l, fail.get(COMMON.RECEIVE_DATA_SIZE));
        worker.stopSending();
    }

    @Test
    public void checkCLI() throws IOException {
        //check if simple cli works
        //	public CLIWorker(String taskID, Connection connection, String queriesFile, @Nullable Integer timeOut, @Nullable Integer timeLimit, @Nullable Integer fixedLatency, @Nullable Integer gaussianLatency, Integer workerID) {
        Connection con = new Connection();
        con.setUser("user1");
        con.setPassword("pwd");

        con.setEndpoint("/bin/echo \"$QUERY$ $USER$:$PASSWORD$ $ENCODEDQUERY$\" > "+f.getAbsolutePath());
        CLIWorker worker = new CLIWorker("123/1/1", con, "src/test/resources/update/empty.nt", null, null, null, null, 1);
        worker.executeQuery("test ()", "1");
        String content = FileUtils.readFile(f.getAbsolutePath());
        assertEquals("test () user1:pwd test+%28%29\n", content);

        con = new Connection();
        String cmd = "/bin/printf ";
        if (SystemUtils.IS_OS_MAC) {
            cmd = "/usr/bin/printf ";
        }
        con.setEndpoint("/bin/echo \"$QUERY$ $USER$:$PASSWORD$ $ENCODEDQUERY$\" > " + f.getAbsolutePath() + " | " + cmd + " \"HeaderDoesNotCount\na\na\"");
        worker = new CLIWorker("123/1/1", con, "src/test/resources/update/empty.nt", null, null, null, null, 1);
        worker.executeQuery("test ()", "1");
        content = FileUtils.readFile(f.getAbsolutePath());
        assertEquals("test () : test+%28%29\n", content);
        Collection<Properties> results  = worker.popQueryResults();
        assertEquals(1, results.size());
        Properties p = results.iterator().next();
        assertEquals(2l, p.get(COMMON.RECEIVE_DATA_SIZE));
    }
}
