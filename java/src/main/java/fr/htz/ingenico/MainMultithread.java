package fr.htz.ingenico;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yaml.snakeyaml.Yaml;


public class MainMultithread   {
	String[] args;
	Yaml yaml = new Yaml();
    public MainMultithread(String[] args) {
		this.args = args;
	}

    protected ExecutorService threadPool;
    final int numThreads= 200;
	List<Context> contexts = new LinkedList<Context>();

    static class Context  {
    	public Context (String key , String url) {
    		this.key  = key;
    		this.address  = url;
    				
		}
		final String key;
		final String address;
		boolean success= false;
    	Future<Void> result;
		public Throwable exception = null;
    }
    
    public void launch( ) throws Exception {
     	if ( args.length == 0) {
    		throw new IllegalStateException("Expected input file argument");
    	}
        threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
    	for  ( String arg : args) {

    		doChecksOn(arg);
    	}
		for ( Context ctx : contexts ) {
			ctx.result.get();

			System.out.println(String.format("%s: %s", ctx.success ? "OK":"BAD", ctx.key));
//			System.out.println(String.format("%s: %s%s", ctx.success ? "OK":"BAD", ctx.key, (ctx.exception == null ? "" :ctx.exception .toString() ) ));
		}

        threadPool.shutdown();
        threadPool.awaitTermination(10000, TimeUnit.SECONDS);
 

    }

    private void doChecksOn(String arg) throws  Exception {
		File inputFile = new File(arg);
		try ( InputStream inputStream = new FileInputStream(inputFile)) {
			Map<String, Object> obj = yaml.load(inputStream);
			Map<String, Object> checks = getMap(obj , "checks");
			Map<String, Object> ping = getMap(checks, "ping");
			
			for ( Map.Entry<String, Object> ent : ping.entrySet()) {
				String key = ent.getKey();
				String value = (String)ent.getValue();
	    		final Context here = new Context (key, value);
	            Future<Void> future = threadPool.submit(new Callable<Void>() {

						@Override
						public Void call() throws Exception {
							checkConnectivityFor(here);
							return null;
						}
	            	
	            });
	            here.result = future;
	            contexts.add(here);

			}
		}
		
	}

	private void checkConnectivityFor(final Context testToDo) {
		Socket socket = new Socket();
		try {
			String parts[] = testToDo.address.split(":");
			if ( parts.length != 2) 
	    		throw new IllegalStateException("Unexpected format in "+testToDo.address);
			String host = parts[0];
			int port = 0;
			try {
				port = Integer.parseInt(parts[1]);
			} catch (Exception e) {
	    		throw new IllegalStateException("Unexpected number format in "+parts[1]);
			}
			InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(host), port);
			socket.connect(endpoint, 30000);
			testToDo.success = true;
		} catch (Exception e) {
			testToDo.success = false;
			testToDo.exception  = e;
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
			}
		}
		
	}

	private Map<String, Object> getMap(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if ( value == null)
    		throw new IllegalStateException("no  "+key+" key in input");
		if ( !(value instanceof Map))
    		throw new IllegalStateException("Expected a map for key "+key+" but got a "+value.getClass().getName() );
		return (Map<String,Object> )value;
	}

	public static void main(String[] args) throws Exception{
    	MainMultithread me = new MainMultithread(args);
    	me.launch();
    }

}