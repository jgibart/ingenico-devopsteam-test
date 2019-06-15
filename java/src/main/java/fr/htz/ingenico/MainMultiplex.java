package fr.htz.ingenico;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.yaml.snakeyaml.Yaml;

public class MainMultiplex {
	//30 seconds connect timeout
	private static final long timeout = 10000;
	String[] args;
	Yaml yaml = new Yaml();

	public MainMultiplex(String[] args) {
		this.args = args;
	}

	final int numSockets = 200;
	List<Context> allContexts = new LinkedList<Context>();
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

		for ( String arg : args)
			readTodo(arg);
		handleSockets();
		
		
		selector.close();

	}


	static Logger logger = Logger.getLogger("");
	Thread submittingThread;

	void handleSockets() throws IOException {
		todoIt = todo.iterator();
		while (selector.isOpen()) {
			injectWork();
			if ( selector.keys().size() == 0 && ! todoIt.hasNext())
				break;
	        logger.fine("we have "+selector.keys().size()+" sockets to handle ");
	        if (selector.select(1000) > 0) {
		        Set<SelectionKey> selectedKeys = selector.selectedKeys();
		        logger.finer("we have "+selectedKeys.size()+"/"+selector.keys().size()+" sockets to process");
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
			manageTimeouts();
        	reportDone() ;
		}

	}


	private void injectWork() {
		while ( selector.keys().size() < numSockets && todoIt.hasNext()) {
			Context ctx = todoIt.next();
			checkConnectivityFor(ctx);
		}
		
	}

	List<Context> todo = new ArrayList<>(1000);
	Iterator<Context> todoIt;
	private void readTodo(String arg) throws Exception {
		File inputFile = new File(arg);
		try (InputStream inputStream = new FileInputStream(inputFile)) {
			Map<String, Object> obj = yaml.load(inputStream);
			Map<String, Object> checks = getMap(obj, "checks");
			Map<String, Object> ping = getMap(checks, "ping");

			for (Map.Entry<String, Object> ent : ping.entrySet()) {
				String key = ent.getKey();
				String value = (String) ent.getValue();
				final Context here = new Context(key, value);
				todo.add(here);
			}
		}

	}

	private void checkConnectivityFor(final Context testToDo) {
		try {
			logger.fine("submitting "+testToDo.address);
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
			InetAddress inetAddress = InetAddress.getByName(host);
			InetSocketAddress endpoint = new InetSocketAddress(inetAddress , port);

			testToDo.socketChannel = SocketChannel.open();
			testToDo.socketChannel.configureBlocking(false);
			testToDo.selkey =  testToDo.socketChannel.register(selector, SelectionKey.OP_CONNECT, testToDo);
			boolean connected = testToDo.socketChannel.connect(endpoint);
			testToDo.connectTime = System.currentTimeMillis();
				
			allContexts.add(testToDo);
			if (connected) {
				finishConnect(testToDo);
			}
			logger.finer("submitted  "+testToDo.address);
		} catch (Exception e) {
			testToDo.success = false;
			testToDo.done = true;
			testToDo.exception = e;
			logger.fine("error "+e);
		}

	}

	private void finishConnect(Context testToDo) {
		try {
			testToDo.success= testToDo.socketChannel.finishConnect();
			testToDo.selkey.cancel();
			testToDo.socketChannel.configureBlocking(true);
			testToDo.socketChannel.setOption(StandardSocketOptions.SO_LINGER, 0);
			testToDo.socketChannel.close();

			
		} catch (Exception e) {
			testToDo.success = false;
			testToDo.exception = e;
		} finally {
			testToDo.done = true;
		}
		long end = System.currentTimeMillis();
		long ms = end - testToDo.connectTime;
		logger.fine("got result for   "+testToDo.key+ " in "+ms +" ms  . Allresults is "+allContexts.size()+ " pending connects "+selector.keys().size());
		reportDone();

	}
	private void manageTimeouts() {
		long now = System.currentTimeMillis();
		for ( SelectionKey key : selector.keys()) {
			Context ctx = (Context )key.attachment();
			long elapsed = now - ctx.connectTime;
			if ( elapsed > timeout) {
				//timeout
				key .cancel();
				try {
					if ( ctx.socketChannel != null)
						ctx.socketChannel.close();
					
				} catch (IOException e) {
				}
				ctx.exception = new ConnectException("timeout after "+timeout +" ms");
				ctx.done = true;
				logger.fine("timout for  "+ ctx.key);
			}
		}
	}

	private void reportDone() {
		Iterator<Context> it = allContexts.iterator();
		boolean allDone = true;
		while ( it.hasNext()) {
			Context ctx = it.next();
			if ( !ctx.done)
				allDone = false;
			else {
				if ( allDone)  {
					it.remove();
					System.out.println(String.format("%s: %s", ctx.success ? "OK":"BAD", ctx.key));
//					System.out.println(String.format("%s: %s%s", ctx.success ? "OK":"BAD", ctx.key, (ctx.exception == null ? "" :ctx.exception .toString() ) ));
					
				}
			}

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