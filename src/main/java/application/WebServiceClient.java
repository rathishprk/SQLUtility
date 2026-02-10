package application;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class WebServiceClient {
    
    /**
     * Load XML string into Document object with proper error handling
     */
    public static Document loadXMLString(String response) throws Exception {
        // Clean the response before parsing
        response = cleanXMLResponse(response);
        
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setIgnoringElementContentWhitespace(true);
        
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(response));
        is.setEncoding("UTF-8");

        return db.parse(is);
    }
    
    /**
     * Clean XML response by removing BOM, whitespace, and non-XML content
     */
    private static String cleanXMLResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        
        // Remove UTF-8 BOM (U+FEFF) if present
        if (response.startsWith("\uFEFF")) {
            response = response.substring(1);
        }
        
        // Remove UTF-8 BOM bytes if present (EF BB BF)
        if (response.length() >= 3 && 
            response.charAt(0) == '\u00EF' && 
            response.charAt(1) == '\u00BB' && 
            response.charAt(2) == '\u00BF') {
            response = response.substring(3);
        }
        
        // Trim leading and trailing whitespace
        response = response.trim();
        
        // Find actual XML start
        int xmlStart = response.indexOf("<?xml");
        if (xmlStart > 0) {
            System.out.println("Warning: Found content before <?xml at position " + xmlStart);
            response = response.substring(xmlStart);
        } else if (!response.startsWith("<?xml") && !response.startsWith("<")) {
            // If no XML declaration and doesn't start with <, find first < tag
            int tagStart = response.indexOf("<");
            if (tagStart > 0) {
                System.out.println("Warning: Found content before first tag at position " + tagStart);
                response = response.substring(tagStart);
            }
        }
        
        return response;
    }
    
    /**
     * Print full response with character details for debugging..
     */
    private static void debugPrintResponse(String response) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DETAILED RESPONSE ANALYSIS");
        System.out.println("=".repeat(80));
        System.out.println("Response length: " + response.length());
        
        // Print all bytes in hex
        System.out.println("\nAll bytes (hex):");
        for (int i = 0; i < response.length(); i++) {
            System.out.printf("%02X ", (int) response.charAt(i));
            if ((i + 1) % 20 == 0) System.out.println();
        }
        System.out.println();
        
        // Print all characters
        System.out.println("\nAll characters:");
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c >= 32 && c <= 126) {
                System.out.print(c);
            } else {
                System.out.print("[" + String.format("%02X", (int)c) + "]");
            }
        }
        System.out.println();
        
        // Print full text
        System.out.println("\nFull response text:");
        System.out.println(response);
        System.out.println("=".repeat(80));
    }
    
    /**
     * Convert string between base64 encoding and decoding
     */
    private String convertString(String inputString, String operation) {
        String outputString = null;
        if (operation.equals("DECODE")) {
            byte[] decoded = Base64.getMimeDecoder().decode(inputString);
            outputString = new String(decoded);
        } else if (operation.equals("ENCODE")) {
            byte[] encoded = Base64.getMimeEncoder().encode(inputString.getBytes());
            outputString = new String(encoded);
        }
        return outputString;
    }

    /**
     * Execute SOAP request to run BIP report with SQL query
     */
    public String[][] postSOAPXML(String queryString, String host, String username, String pwd) {
        String resp = null;
        String encodedSQLQuery = null;
        String[][] string2DArray = null;
        DefaultHttpClient httpclient = null;
        
        try {
            // Encode SQL query
            encodedSQLQuery = convertString(queryString, "ENCODE");
            System.out.println("Encoded SQL Query: " + encodedSQLQuery);
            
            // Build SOAP request body
            String soapBody = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:pub=\"http://xmlns.oracle.com/oxp/service/PublicReportService\">\r\n"
                    + "   <soap:Header/>\r\n"
                    + "   <soap:Body>\r\n"
                    + "      <pub:runReport>\r\n"
                    + "         <pub:reportRequest>\r\n"
                    + "            <pub:flattenXML>false</pub:flattenXML>\r\n"
                    + "            <pub:parameterNameValues>\r\n"
                    + "                  <pub:item>\r\n"
                    + "                  <pub:name>query1</pub:name>\r\n"
                    + "                  <pub:values>\r\n"
                    + "                      <pub:item>" + encodedSQLQuery + "</pub:item>\r\n"
                    + "                  </pub:values>\r\n"
                    + "                  </pub:item>\r\n"
                    + "            </pub:parameterNameValues>\r\n"
                    + "            <pub:reportAbsolutePath>/Custom/CloudTools/V5/SQLConnectReport.xdo</pub:reportAbsolutePath>\r\n"
                    + "            <pub:sizeOfDataChunkDownload>-1</pub:sizeOfDataChunkDownload>\r\n"
                    + "         </pub:reportRequest> \r\n"
                    + "      </pub:runReport>\r\n"
                    + "   </soap:Body>\r\n"
                    + "</soap:Envelope>";
            
            // Setup HTTP client with credentials
            httpclient = new DefaultHttpClient();
            httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(username, pwd));
            
            // Create HTTP POST request
            StringEntity strEntity = new StringEntity(soapBody, "UTF-8");
            HttpPost post = new HttpPost(host + "/xmlpserver/services/ExternalReportWSSService?wsdl");
            strEntity.setContentType("application/soap+xml");
            post.setEntity(strEntity);
            
            // Execute request
            System.out.println("Executing SOAP request to: " + host);
            HttpResponse response = httpclient.execute(post);
            HttpEntity respEntity = response.getEntity();

            // Check HTTP status code
            int statusCode = response.getStatusLine().getStatusCode();
            System.out.println("HTTP Status Code: " + statusCode);
            System.out.println("HTTP Status Message: " + response.getStatusLine().getReasonPhrase());

            if (respEntity != null) {
                // Get response
                resp = EntityUtils.toString(respEntity, "UTF-8");
                
                // Debug print full response
                debugPrintResponse(resp);
                
                // Clean response
                resp = cleanXMLResponse(resp);
                
                // Parse XML response
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                        new InputSource(new StringReader(resp)));
                
                // Get XPath
                XPathFactory xpf = XPathFactory.newInstance();
                XPath xpath = xpf.newXPath();
                
                // Extract encoded report bytes
                String encodedReportBytes = (String) xpath.evaluate(
                        "/Envelope/Body/runReportResponse/runReportReturn/reportBytes", 
                        document, 
                        XPathConstants.STRING);
                
                if (encodedReportBytes == null || encodedReportBytes.isEmpty()) {
                    System.err.println("Error: reportBytes not found in response");
                    // Try to find if there's a fault
                    String faultString = (String) xpath.evaluate("/Envelope/Body/Fault/faultstring", document, XPathConstants.STRING);
                    if (faultString != null && !faultString.isEmpty()) {
                        System.err.println("SOAP Fault: " + faultString);
                    }
                    return null;
                }
                
                // Decode report bytes
                byte[] decoded = Base64.getMimeDecoder().decode(encodedReportBytes);
                String decodedString = new String(decoded, "UTF-8");
                System.out.println("Decoded report data length: " + decodedString.length());
                System.out.println("Decoded string preview: " + decodedString.substring(0, Math.min(500, decodedString.length())));
                
                // Parse decoded XML
                Document xmlDoc = loadXMLString(decodedString);
                
                // Count rows
                Double countDouble = (Double) xpath.evaluate("count(/ROWSET/ROW)", xmlDoc, XPathConstants.NUMBER);
                int count = (int) Math.round(countDouble);
                System.out.println("Row count: " + count);
                
                if (count == 0) {
                    System.out.println("No data rows found in report");
                    return null;
                }
                
                // Get column names from first row
                NodeList allElementNames = (NodeList) xpath.evaluate("/ROWSET/ROW[1]//*", xmlDoc, XPathConstants.NODESET);
                NodeList allElementValues = (NodeList) xpath.evaluate("/ROWSET/ROW//*", xmlDoc, XPathConstants.NODESET);
                
                ArrayList<String> elementNames = new ArrayList<>();
                ArrayList<String> elementValues = new ArrayList<>();
                string2DArray = new String[count + 1][allElementNames.getLength()];
                
                // Extract column names
                for (int i = 0; i < allElementNames.getLength(); i++) {
                    Node currentElement = allElementNames.item(i);
                    elementNames.add(i, currentElement.getNodeName());
                    string2DArray[0][i] = currentElement.getNodeName();
                }
                
                System.out.println("Column count: " + allElementNames.getLength());
                System.out.println("Total element values: " + allElementValues.getLength());
                
                // Extract data values
                int size = 0;
                for (int h = 1; h <= count; h++) {
                    for (int i = 0; i < allElementNames.getLength(); i++) {
                        if (size < allElementValues.getLength()) {
                            Node currentElement = allElementValues.item(size);
                            // Check if element has child elements (nested)
                            boolean hasChildren = xpath.evaluate("*", currentElement, XPathConstants.NODE) != null;
                            elementValues.add(size, hasChildren ? null : currentElement.getTextContent());
                            string2DArray[h][i] = elementValues.get(size);
                            size++;
                        }
                    }
                }
                
                System.out.println("Data extraction completed successfully");
                
            } else {
                System.err.println("No Response entity received");
            }

        } catch (Exception e) {
            System.err.println("WebService SOAP exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up HTTP client
            if (httpclient != null) {
                httpclient.getConnectionManager().shutdown();
            }
        }
        
        return string2DArray;
    }
    
    /**
     * Check credentials by attempting to get report definition
     */
    public String checkCredentials(String getReportDefinition, String username, String password) {
        String getresponse = null;
        String result = null;
        DefaultHttpClient httpclientrequest = null;
        
        try {
            // Build SOAP request body
            String Soaprequestbody = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:v2=\"http://xmlns.oracle.com/oxp/service/v2\">\r\n"
                    + "   <soapenv:Header/>\r\n"
                    + "   <soapenv:Body>\r\n"
                    + "      <v2:getReportDefinition>\r\n"
                    + "         <v2:reportAbsolutePath>/Custom/CloudTools/V5/SQLConnectReport.xdo</v2:reportAbsolutePath>\r\n"
                    + "         <v2:userID>" + username + "</v2:userID>\r\n"
                    + "         <v2:password>" + password + "</v2:password>\r\n"
                    + "      </v2:getReportDefinition>\r\n"
                    + "   </soapenv:Body>\r\n"
                    + "</soapenv:Envelope>";
            
            System.out.println("\nSOAP Request being sent:");
            System.out.println(Soaprequestbody);
            
            // Setup HTTP client
            httpclientrequest = new DefaultHttpClient();
            httpclientrequest.getCredentialsProvider().setCredentials(
                    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(username, password));
            
            // Create HTTP POST request
            StringEntity stringEntity = new StringEntity(Soaprequestbody, "UTF-8");
            HttpPost postrequest = new HttpPost(getReportDefinition);
            stringEntity.setContentType("application/soap+xml");
            postrequest.setEntity(stringEntity);
            
            // Execute request
            System.out.println("Checking credentials for user: " + username);
            System.out.println("URL: " + getReportDefinition);
            HttpResponse httpresponse = httpclientrequest.execute(postrequest);
            
            // Check HTTP status
            int statusCode = httpresponse.getStatusLine().getStatusCode();
            System.out.println("HTTP Status Code: " + statusCode);
            System.out.println("HTTP Status Message: " + httpresponse.getStatusLine().getReasonPhrase());
            
            HttpEntity responseEntity = httpresponse.getEntity();
            
            if (responseEntity != null) {
                // Get response
                getresponse = EntityUtils.toString(responseEntity, "UTF-8");
                
                // Debug print full response
                debugPrintResponse(getresponse);
                
                // If response is too short or doesn't look like XML, it might be an error
                if (getresponse.length() < 100 || !getresponse.contains("<")) {
                    System.err.println("ERROR: Response doesn't appear to be valid XML");
                    System.err.println("This might indicate:");
                    System.err.println("  - Wrong endpoint URL");
                    System.err.println("  - Network/firewall issue");
                    System.err.println("  - Service is down");
                    System.err.println("  - Invalid credentials blocking at proxy/gateway level");
                    return "FAULT";
                }
                
                // Clean response
                getresponse = cleanXMLResponse(getresponse);
                System.out.println("\nCleaned response:");
                System.out.println(getresponse);
                
                // Parse XML response
                Document documentresp = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                        new InputSource(new StringReader(getresponse)));
                
                // Get XPath
                XPathFactory xpf = XPathFactory.newInstance();
                XPath xpath = xpf.newXPath();
                
                // Check for fault
                String faultExists = (String) xpath.evaluate("count(/Envelope/Body/Fault)", documentresp, XPathConstants.STRING);
                System.out.println("Fault count: " + faultExists);
                
                int faultCount = Integer.parseInt(faultExists);
                if (faultCount == 1) {
                    result = "FAULT";
                    System.out.println("Credentials check: FAILED");
                    
                    // Try to extract fault message
                    String faultString = (String) xpath.evaluate("/Envelope/Body/Fault/faultstring", documentresp, XPathConstants.STRING);
                    if (faultString != null && !faultString.isEmpty()) {
                        System.out.println("Fault message: " + faultString);
                    }
                    String faultDetail = (String) xpath.evaluate("/Envelope/Body/Fault/detail", documentresp, XPathConstants.STRING);
                    if (faultDetail != null && !faultDetail.isEmpty()) {
                        System.out.println("Fault detail: " + faultDetail);
                    }
                } else {
                    result = "SUCCESS";
                    System.out.println("Credentials check: SUCCESS");
                }
                
            } else {
                System.err.println("No Response entity received");
                result = "FAULT";
            }
            
        } catch (Exception exc) {
            System.err.println("WebService SOAP exception: " + exc.getMessage());
            exc.printStackTrace();
            result = "FAULT";
        } finally {
            // Clean up HTTP client
            if (httpclientrequest != null) {
                httpclientrequest.getConnectionManager().shutdown();
            }
        }
        
        return result;
    }
    
    /**
     * Extract Element nodes from NodeList
     */
    public List<Element> getElements(final NodeList nodeList) {
        final int len = nodeList.getLength();
        final List<Element> elements = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            final Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                elements.add((Element) node);
            }
        }
        return elements;
    }
    
    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        WebServiceClient client = new WebServiceClient();
        
        try {
            String host = "https://fa-ibblqy-test.fa.ocs.oraclecloud.com";
            String username = "svc_integration_user";
            String password = "Altera@123";
            String reportServiceUrl = host + "/xmlpserver/services/v2/ReportService?WSDL";
            
            // Test credentials
            System.out.println("=".repeat(80));
            System.out.println("Testing credentials...");
            System.out.println("=".repeat(80));
            String credentialResult = client.checkCredentials(reportServiceUrl, username, password);
            System.out.println("\n" + "=".repeat(80));
            System.out.println("FINAL CREDENTIAL CHECK RESULT: " + credentialResult);
            System.out.println("=".repeat(80));
            
            // If credentials are valid, execute a test query
            if ("SUCCESS".equals(credentialResult)) {
                System.out.println("\n" + "=".repeat(80));
                System.out.println("Executing test query...");
                System.out.println("=".repeat(80));
                
                String testQuery = "select * from ap_invoices_all where rownum < 5";
                String[][] results = client.postSOAPXML(testQuery, host, username, password);
                
                if (results != null) {
                    System.out.println("\n" + "=".repeat(80));
                    System.out.println("QUERY RESULTS");
                    System.out.println("=".repeat(80));
                    System.out.println("Total rows (including header): " + results.length);
                    System.out.println("Column count: " + (results.length > 0 ? results[0].length : 0));
                    
                    // Print results in table format
                    for (int i = 0; i < results.length && i < 6; i++) {
                        if (i == 0) {
                            System.out.println("\n=== COLUMN NAMES ===");
                        } else {
                            System.out.println("\n=== DATA ROW " + i + " ===");
                        }
                        for (int j = 0; j < results[i].length && j < 10; j++) {
                            System.out.println("  [" + j + "] " + results[i][j]);
                        }
                    }
                } else {
                    System.out.println("No results returned from query");
                }
            } else {
                System.out.println("Skipping query execution due to invalid credentials");
            }
            
        } catch (Exception e) {
            System.err.println("Error in main: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Execution completed");
        System.out.println("=".repeat(80));
    }
}