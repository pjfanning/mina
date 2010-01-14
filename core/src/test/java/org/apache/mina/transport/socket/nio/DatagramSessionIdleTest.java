package org.apache.mina.transport.socket.nio;

import java.net.InetSocketAddress;

import junit.framework.TestCase;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

public class DatagramSessionIdleTest extends TestCase {

    private boolean readerIdleReceived;

    private boolean writerIdleReceived;

    private boolean bothIdleReceived;

    private Object mutex = new Object();

    private class TestHandler extends IoHandlerAdapter {

        @Override
        public void sessionIdle(IoSession session, IdleStatus status)
                throws Exception {
            if (status == IdleStatus.BOTH_IDLE) {
                bothIdleReceived = true;
            } else if (status == IdleStatus.READER_IDLE) {
                readerIdleReceived = true;
            } else if (status == IdleStatus.WRITER_IDLE) {
                writerIdleReceived = true;
            }
            
            synchronized (mutex) {
                mutex.notifyAll();
            }
            
            super.sessionIdle(session, status);
        }
    }

    public void testSessionIdle() throws Exception {
        final int READER_IDLE_TIME = 3;//seconds
        final int WRITER_IDLE_TIME = READER_IDLE_TIME + 2;//seconds
        final int BOTH_IDLE_TIME = WRITER_IDLE_TIME + 2;//seconds
        
        NioDatagramAcceptor acceptor = new NioDatagramAcceptor();
        acceptor.getSessionConfig().setBothIdleTime(BOTH_IDLE_TIME);
        acceptor.getSessionConfig().setReaderIdleTime(READER_IDLE_TIME);
        acceptor.getSessionConfig().setWriterIdleTime(WRITER_IDLE_TIME);
        InetSocketAddress bindAddress = new InetSocketAddress(58465);
        acceptor.setHandler(new TestHandler());
        acceptor.bind(bindAddress);
        IoSession session = acceptor.newSession(new InetSocketAddress(
                "127.0.0.1", 56372), bindAddress);
        
        //check properties to be copied from acceptor to session
        assertEquals(BOTH_IDLE_TIME, session.getConfig().getBothIdleTime());
        assertEquals(READER_IDLE_TIME, session.getConfig().getReaderIdleTime());
        assertEquals(WRITER_IDLE_TIME, session.getConfig().getWriterIdleTime());
        
        //verify that IDLE events really received by handler
        long startTime = System.currentTimeMillis();
        
        synchronized (mutex) {
            while (!readerIdleReceived
                    && (System.currentTimeMillis() - startTime) < (READER_IDLE_TIME + 1) * 1000)
                try {
                    mutex.wait(READER_IDLE_TIME * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        
        assertTrue(readerIdleReceived);
        
        synchronized (mutex) {
            while (!writerIdleReceived
                    && (System.currentTimeMillis() - startTime) < (WRITER_IDLE_TIME + 1) * 1000)
                try {
                    mutex.wait((WRITER_IDLE_TIME - READER_IDLE_TIME) * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        
        assertTrue(writerIdleReceived);
        
        synchronized (mutex) {
            while (!bothIdleReceived
                    && (System.currentTimeMillis() - startTime) < (BOTH_IDLE_TIME + 1) * 1000)
                try {
                    mutex.wait((BOTH_IDLE_TIME - WRITER_IDLE_TIME) * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
        
        assertTrue(bothIdleReceived);
    }
}