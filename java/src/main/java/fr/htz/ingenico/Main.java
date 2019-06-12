package fr.htz.ingenico;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;


public class Main   {
	String[] args;
	Yaml yaml = new Yaml();
    public Main(String[] args) {
		this.args = args;
	}

    public void launch( ) throws Exception {
    	if ( args.length == 0) {
    		throw new IllegalStateException("Expected input file argument");
    	}
    	for  ( String arg : args) {
    		doChecksOn(arg);
    	}
    }

    private void doChecksOn(String arg) throws  Exception {
		File inputFile = new File(arg);
		try ( InputStream inputStream = new FileInputStream(inputFile)) {
			Map<String, Object> obj = yaml.load(inputStream);
			Map<String, Object> checks = getMap(obj , "checks");
			Map<String, Object> ping = getMap(checks, "ping");
			
			for ( Map.Entry<String, Object> ent : ping.entrySet()) {
				checkConnectivityFor(ent.getKey(), (String)ent.getValue());
			}
		}
		
	}

	private void checkConnectivityFor(String name, String address) {
		Socket socket = new Socket();
		try {
			String parts[] = address.split(":");
			if ( parts.length != 2) 
	    		throw new IllegalStateException("Unexpected format in "+address);
			String host = parts[0];
			int port = 0;
			try {
				port = Integer.parseInt(parts[1]);
			} catch (Exception e) {
	    		throw new IllegalStateException("Unexpected number format in "+parts[1]);
			}
			InetSocketAddress endpoint = new InetSocketAddress(InetAddress.getByName(host), port);
			socket.connect(endpoint, 3000);
			System.out.println("OK: "+name);
		} catch (Exception e) {
			System.out.println("BAD: "+name);
			//Could also print the cause
			//System.out.println("BAD: "+name + "("+e.toString()+")");
			
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
    	Main me = new Main(args);
    	me.launch();
    }

}