package fr.htz.ingenico;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yaml.snakeyaml.Yaml;

public class MainMultiplex {
	//30 seconds connect timeout
	private static final long timeout = 30000;
	String[] args;
	Yaml yaml = new Yaml();

	public MainMultiplex(String[] args) {
		this.args = args;
	}

	final int numSockets = 200;
	BlockingQueue<Context> contexts = new ArrayBlockingQueue<Context>(numSockets);
	Selector selector = null;

	static class Context {
		public Context(String key, String url) {
			this.key = key;
			this.address = url;

		}

		final String key;
		final String address;
		boolean success = false;
		volatile boolean done = false;
		SocketChannel socketChannel;
		SelectionKey selkey;
		long connectTime = 0;
		public Throwable exception = null;
	}

	public void launch() throws Exception {
		if (args.length == 0) {
			throw new IllegalStateException("Expected input file argument");
		}
		selector = Selector.open();

		startSubmittingThread();

		handleSockets();
		
		
		selector.close();

	}
	private volatile boolean allSubmitted = false;

	static Logger logger = Logger.getLogger("");
	Thread submittingThread;

	void handleSockets() throws IOException {
		while (selector.isOpen()) {
			if ( selector.keys().size() == 0 && allSubmitted)
				break;
	        if (selector.select(1000) <= 0)
	             continue;		
	        Set<SelectionKey> selectedKeys = selector.selectedKeys();
	        logger.info("we have "+selectedKeys.size()+"/"+selector.keys().size()+" to handle while queue is "+contexts.size());
			Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
			while (keyIterator.hasNext()) {

				SelectionKey key = keyIterator.next();
				Context ctx = (Context) key.attachment();
				if (!key.isValid()) {
					ctx.success = false;
					ctx.exception = new IllegalStateException("selector key invalid");
				} else if (key.isConnectable()) {
					// a connection was established with a remote server.
					finishConnect(ctx);
				}
				ctx.done = true;

				keyIterator.remove();
			}
		}

	}
	private void startSubmittingThread() {
		submittingThread = new Thread(new Runnable() {


			@Override
			public void run() {
				try {
					for (String arg : args) {
						doChecksOn(arg);
					}

				} catch (Exception e) {
					logger.log(Level.SEVERE, "dying with ", e);
				}
				allSubmitted = true;
			}
		});

		submittingThread.start();

	}


	private void doChecksOn(String arg) throws Exception {
		File inputFile = new File(arg);
		try (InputStream inputStream = new FileInputStream(inputFile)) {
			Map<String, Object> obj = yaml.load(inputStream);
			Map<String, Object> checks = getMap(obj, "checks");
			Map<String, Object> ping = getMap(checks, "ping");

			for (Map.Entry<String, Object> ent : ping.entrySet()) {
				String key = ent.getKey();
				String value = (String) ent.getValue();
				final Context here = new Context(key, value);
				checkConnectivityFor(here);

 
			}
		}

	}

	private void checkConnectivityFor(final Context testToDo) {
		try {
			logger.fine("submitting "+testToDo.address);
			long start = System.currentTimeMillis();
			String parts[] = testToDo.address.split(":");
			if (parts.length != 2)
				throw new IllegalStateException("Unexpected format in " + testToDo.address);
			String host = parts[0];
			int port = 0;
			try {
				port = Integer.parseInt(parts[1]);
			} catch (Exception e) {
				throw new IllegalStateException("Unexpected number format in " + parts[1]);
			}
			InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(host), port);
			long dns = System.currentTimeMillis();
			contexts.put(testToDo);
			testToDo.socketChannel = SocketChannel.open();
			testToDo.socketChannel.configureBlocking(false);
			testToDo.selkey =  testToDo.socketChannel.register(selector, SelectionKey.OP_CONNECT, testToDo);
			boolean connected = testToDo.socketChannel.connect(endpoint);
			testToDo.connectTime = System.currentTimeMillis();
			if (connected) {
				finishConnect(testToDo);
			}
			long end = System.currentTimeMillis();
			long ms = end - start;
			logger.finer("submitted  "+testToDo.address+ " in "+ms +" ms. dns in "+(dns - start)+"ms.  Queue is now "+contexts.size());
		} catch (Exception e) {
			testToDo.success = false;
			testToDo.done = true;
			testToDo.exception = e;
		}

	}

	private void finishConnect(Context testToDo) {
		try {
			testToDo.success= testToDo.socketChannel.finishConnect();
			testToDo.selkey.cancel();
			testToDo.socketChannel.close();
		} catch (Exception e) {
			testToDo.success = false;
			testToDo.exception = e;
		} finally {
			testToDo.done = true;
		}
		long end = System.currentTimeMillis();
		long ms = end - testToDo.connectTime;
		System.out.println("got result for   "+testToDo.key+ " in "+ms +" ms");
		reportDone();

	}

	private void reportDone() {
		Iterator<Context> it = contexts.iterator();
		long now = System.currentTimeMillis();
		while ( it.hasNext()) {
			Context ctx = it.next();
			long elapsed = now - ctx.connectTime;
			if ( elapsed > timeout) {
				//timeout
				try {
					if ( ctx.socketChannel != null)
						ctx.socketChannel.close();
				} catch (IOException e) {
				}
				ctx.exception = new ConnectException("timeout after "+timeout +" ms");
				ctx.done = true;
			}
			if ( !ctx.done)
				break;
			it.remove();
			System.out.println(String.format("%s: %s", ctx.success ? "OK":"BAD", ctx.key));
//			System.out.println(String.format("%s: %s%s", ctx.success ? "OK":"BAD", ctx.key, (ctx.exception == null ? "" :ctx.exception .toString() ) ));

		}
		
	}


	private Map<String, Object> getMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null)
			throw new IllegalStateException("no  " + key + " key in input");
		if (!(value instanceof Map))
			throw new IllegalStateException(
					"Expected a map for key " + key + " but got a " + value.getClass().getName());
		return (Map<String, Object>) value;
	}

	public static void main(String[] args) throws Exception {
		MainMultiplex me = new MainMultiplex(args);
		me.launch();
	}

}