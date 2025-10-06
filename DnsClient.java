import java.util.Base64;
import java.util.Random;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

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
            //System.out.println(domainName);
        } catch (Exception e) {
            System.out.println("Error: In correct input syntax");
        }

        byte[] bytes = constructRequest();
        
        // UDP socket logic
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(timeout);
            InetAddress serverAddr = InetAddress.getByName(address);
            DatagramPacket request = new DatagramPacket(bytes, bytes.length, serverAddr, port);
            DatagramPacket response = new DatagramPacket(new byte[512], 512);
            int attempts = 0;
            boolean receivedResponse = false;
            while (attempts < retries && !receivedResponse) {
                try {
                    System.out.println("DnsClient sending request for " + domainName);
                    System.out.println("Server: " + address);
                    System.out.println("Request type: " + queryType);

                    double start, end;
                    start = System.currentTimeMillis();

                    socket.send(request);
                    socket.receive(response);

                    end = System.currentTimeMillis();

                    double responseTime = (end - start) / 1000;
                    receivedResponse = true;
                    System.out.println("Response received after " + responseTime + " seconds " + "(" + retries + ") retries");
                } catch (Exception e) {
                    attempts++;
                    if (attempts == retries) {
                        System.out.println("ERROR\tMaximum number of retries " + retries + " exceeded");
                        return;
                    }
                    System.out.println("TIMEOUT\tRetrying...");
                }
            }
            socket.close();
            // Process response
            byte[] responseData = new byte[response.getLength()];
            System.arraycopy(response.getData(), 0, responseData, 0, response.getLength());
            //printBytes(responseData);
            parseResponse(responseData);

        } catch (Exception e) {
            System.out.println("Error with UDP socket: " + e.getMessage());
        }

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
    public static void parseResponse(byte[] response) {
        ByteBuffer buffer = ByteBuffer.wrap(response);

        // Parse header
        
        int transactionID = buffer.getShort() & 0xFFFF;
        int flags = buffer.getShort() & 0xFFFF;
        int qdCount = buffer.getShort() & 0xFFFF;
        int anCount = buffer.getShort() & 0xFFFF;
        int nsCount = buffer.getShort() & 0xFFFF;
        int arCount = buffer.getShort() & 0xFFFF;

        // Skip header and question section to start at the answer section
        buffer.position(12 + getDomainLength() + 5);

        // Parse answer section (start here for step-by-step)
        int answerOffset = 12 + getDomainLength() + 5;
        buffer.position(answerOffset);

        // Check if rcode indicates an error
        int rcode = flags & 0xF;
        if (rcode == 1) {
            System.out.println("The name server was unable to interpret the query");
            return;
        }
        if (rcode == 2) {
            System.out.println("Server failure: the name server was unable to process this query due to a problem with the\r\n" + //
                                "name server");
            return;
        }
        if (rcode == 3) {
            System.out.println("Domain not found");
            return;
        }
        if (rcode == 4) {
            System.out.println("Not implemented: the name server does not support the requested kind of query");
            return;
        }
        if (rcode == 5) {
            System.out.println(" Refused: the name server refuses to perform the requested operation for policy reasons");
            return;
        }
        String auth;

        int aa = (flags >> 10) & 1;
        if (aa == 0) {
            auth = "nonauth";
        } else {
            auth = "auth";
        }
        // Parse answers
        System.out.printf("***Answer Section (%d records)***\n", anCount);
        for (int i = 0; i < anCount; i++) {

            int name = buffer.getShort() & 0xFFFF;
            int type = buffer.getShort() & 0xFFFF;
            int clazz = buffer.getShort() & 0xFFFF;
            int ttl = buffer.getInt();
            int rdlength = buffer.getShort() & 0xFFFF;
            byte[] rdata = new byte[rdlength];
            buffer.get(rdata);

            if (type == 1 && rdlength == 4) {
                // A record
                System.out.println("IP\t" + (rdata[0] & 0xFF) + "." + (rdata[1] & 0xFF) + "." + (rdata[2] & 0xFF) + "." + (rdata[3] & 0xFF) + "\t" + ttl + "\t" +auth);
            } else if (type == 5) {
                // CNAME
                String domain = decodeDomainName(response, buffer.position() - rdlength);
                System.out.println("CNAME\t" + domain + "\t" + ttl + "\t" + auth);
            }
            else if (type == 2) {
                // NS
                String domain = decodeDomainName(response, buffer.position() - rdlength);
                System.out.println("NS " + domain + "\t" + ttl + "\t" + auth);
            } else if (type == 15) {
                // MX
                int preference = ((rdata[0] & 0xFF) << 8) | (rdata[1] & 0xFF);
                String domain = decodeDomainName(response, buffer.position() - rdlength + 2);
                System.out.println("MX " + preference + "\t" + domain + "\t\t" + ttl + '\t' + auth);
            } else {
                System.out.println("RDATA=" + java.util.Arrays.toString(rdata));
            }
        }

        // Parse additional section
        if (arCount > 0) {
            System.out.println("***Additional Section (" + arCount + " records)***");
        }
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
                case "-ns":
                    queryType = "NS";
                    break;
            }

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

    // Helper to decode DNS names (handles compression)
    public static String decodeDomainName(byte[] data, int offset) {
        StringBuilder name = new StringBuilder();
        int i = offset;
        boolean jumped = false;
        int jumpPos = -1;
        while (i < data.length) {
            int len = data[i] & 0xFF;
            if (len == 0) {
                if (!jumped) i++;
                break;
            }
            if ((len & 0xC0) == 0xC0) {
                int pointer = ((len & 0x3F) << 8) | (data[i + 1] & 0xFF);
                if (!jumped) jumpPos = i + 2;
                i = pointer;
                jumped = true;
                continue;
            } else {
                if (name.length() > 0) name.append(".");
                for (int j = 1; j <= len; j++) {
                    name.append((char) data[i + j]);
                }
                i += len + 1;
            }
        }
        if (jumped && jumpPos != -1) {
            // If we jumped, continue after the pointer
            i = jumpPos;
        }
        return name.toString();
    }
}