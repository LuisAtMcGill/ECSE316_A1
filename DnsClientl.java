import java.util.Random;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.nio.ByteBuffer;

public class DnsClient {

    private static int timeout = 5000;
    private static int retries = 3;
    private static int port = 53;
    private static String queryType = "A";
    private static byte[] server = new byte[4];
    private static String address;
    private static String domainName;

    public static void main(String[] args) {
        try {
            parseArguments(args);
            System.out.println(domainName);
        } catch (Exception e) {
            System.out.println("Error");
        }

        byte[] bytes = constructRequest();
        printBytes(bytes);
    }

    public static byte[] constructRequest() {
        int domainNameLength = getDomainLength();

        // Request length is domain name length + header bytes (12) + question bytes (5)
        ByteBuffer req = ByteBuffer.allocate(domainNameLength + 12 + 5);

        // Put request header
        byte[] reqHeader = getRequestHeader();
        req.put(reqHeader);

        // Put question
        byte[] question = getQuestion(domainNameLength);
        req.put(question);

        return req.array();
    }
    public static byte[] parseResponse() {
        return null;
    }
    public static void printResponse() {

    }

    // For debugging purposes
    public static void printBytes(byte[] bytes) {
        System.out.println(new String(bytes));
    }

    public static void parseArguments(String[] args) {
        for(int i = 0; i < args.length; i++){
            String arg = args[i];
            switch (arg) {
                case "-t":
                    int seconds = Integer.parseInt(args[++i]);
                    timeout = seconds * 1000;
                    break;
                case "-r":
                    retries = Integer.parseInt(args[++i]);
                    break;
                case "-p":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "-mx":
                    queryType = "MX";
                    break;
                case "ns":
                    queryType = "NS";
                    break;
            }
            // Put error handling of no domain name or server provided later
            if (arg.contains("@")) {
                address = arg.substring(1);
                domainName = args[++i];
            }

        }
    }

    private static byte[] getRequestHeader() {
        ByteBuffer header = ByteBuffer.allocate(12);

        // Random id for request header
        byte[] id = new byte[2];
        new Random().nextBytes(id);

        header.put(id);

        /* Values to place for certain sections
           QR OPCODE AA TC RD RA ZZ  RCODE
           0  0000   0  0  1  0  000 0000
         */

        // QR OPCODE AA TC RD -> 0x01
        header.put((byte) 0x01);
        // RA ZZ RCODE -> 0x00
        header.put((byte) 0x00);
        // QDCOUNT is 1 -> 0x0001
        byte[] qdcount = {(byte) 0x00, (byte) 0x01};
        header.put(qdcount);
        // ANCOUNT -> 0x0000
        byte[] ancount =  {(byte) 0x00, (byte) 0x00};
        header.put(ancount);
        // NSCOUNT -> 0x0000
        byte[] nscount = {(byte) 0x00, (byte) 0x00};
        header.put(nscount);
        // ARCOUNT -> 0x0000
        byte[] arcount =  {(byte) 0x00, (byte) 0x00};
        header.put(arcount);

        return header.array();
    }

    private static byte[] getQuestion(int queryNameLength) {
        // Allocate bytes to question byteBuffer
        ByteBuffer q = ByteBuffer.allocate(queryNameLength + 5);

        String[] toks = domainName.split("\\.");
        for (int i = 0; i < toks.length; i++) {
            q.put((byte) toks[i].length());
            for (int j = 0; j < toks[i].length(); j++) {
                q.put((byte) toks[i].charAt(j));
            }
        }

        // Terminate with zero-length octet
        q.put((byte) 0);

        if (queryType.equals("A")) {
            byte[] qtype = {(byte) 0x00, (byte) 0x01};
            q.put(qtype);
        } else if (queryType.equals("NS")) {
            byte[] qtype = {(byte) 0x00, (byte) 0x02};
            q.put(qtype);
        } else if (queryType.equals("MX")) {
            byte[] qtype = {(byte) 0x00, (byte) 0x0f};
            q.put(qtype);
        }

        byte[] qclass = {(byte) 0x00, (byte) 0x01};
        q.put(qclass);

        return q.array();
    }

    private static int getDomainLength() {
        int length = 0;
        String[] toks = domainName.split("\\.");

        for (int i = 0; i < toks.length; i++) {
            length += toks[i].length() + 1;
        }
        return length;
    }

}