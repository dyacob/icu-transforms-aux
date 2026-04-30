import java.io.*;
import com.ibm.icu.text.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.ibm.icu.text.Transliterator;

public class xlit {

	private String inputFile = null;
	private String outputFile = null;
	private String xrules = null;
	private BufferedReader inStream = null;
	private BufferedWriter outStream = null;
	private	int direction = Transliterator.FORWARD ;

	Transliterator transliterator = null;
	
    protected static DocumentBuilder builder = null; 
    {
    	try {
    		builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    	
    		builder.setEntityResolver(new EntityResolver() {
    			public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
    				if (systemId.contains("ldmlSupplemental.dtd")) {
				    File ldmlFile = new File("ldmlSupplemental.dtd");
				    InputStream dtdStream = new FileInputStream(ldmlFile);
					/*
    					ClassLoader classLoader = this.getClass().getClassLoader();
    					InputStream dtdStream = classLoader.getResourceAsStream( "common/dtd/ldmlSupplemental.dtd" );
					*/
    					return new InputSource(dtdStream);
    				} else {
    					return null;
    				}
    			}
    		});
    	}
    	catch( ParserConfigurationException ex) {
    		System.err.println( ex );
    	}
    }
	  
	public xlit() throws IOException { }
	

	public xlit(String[] args) throws Exception {
		processCommandLineArguments( args );
	}

	private static String getCharacterDataFromElement( Element e ) {
		String rules = "";
		NodeList nodeList = e.getChildNodes();
		int size = nodeList.getLength();
		for( int i = 0; i < size; i++ ) {
			Node child = nodeList.item( i );
			if (child instanceof CharacterData) {
				CharacterData cd = (CharacterData) child;
				rules += cd.getData();
			}
			else {
				rules += child.getTextContent();
			}
			
		}
		
		return rules;
		/*
		Node child = e.getFirstChild();
		if (child instanceof CharacterData) {
			CharacterData cd = (CharacterData) child;
			return cd.getData();
		}
		return "";
		*/
	}


	public String readRulesFromStream( InputStream is ) throws IOException {
		String line, segment, rules = "";
		BufferedReader rulesFile = new BufferedReader( new InputStreamReader(is, "UTF-8") );
		while ( (line = rulesFile.readLine()) != null) {
			if ( line.trim().equals("") || line.charAt(0) == '#' ) {
				continue;
			}
			if( line.charAt(line.length()-1) == '\\' ) {
				line = line.substring(0, (line.length()-1) ) ;
			}

			segment = line.replaceFirst ( "^(.*?)#(.*)$", "$1" );
			rules += ( segment == null ) ? line : segment;
		}
		rulesFile.close();
// System.out.println( "Rules Read: " + rules);
		return rules;	
	}


	public String readRulesStringXML( String rulesStringXML ) throws IOException, SAXException {		
		InputStream rulesStream = new ByteArrayInputStream( rulesStringXML.getBytes( StandardCharsets.UTF_8 ) );
		
		InputSource xmlSource = new InputSource( new InputStreamReader( rulesStream, StandardCharsets.UTF_8) );

// System.out.println( "Streams Set" );
	    
		Document doc = builder.parse( xmlSource );
// System.out.println( " Got Doc" );
		NodeList nodes = doc.getElementsByTagName( "tRule" );
// System.out.println( " Got Nodes" );
		Element  element = (Element) nodes.item(0); // assume only one
// System.out.println( " Got Element" );

		String rulesString = getCharacterDataFromElement( element );
		InputStream is = new ByteArrayInputStream( rulesString.getBytes( StandardCharsets.UTF_8 ) );
	    
// System.out.println( "XML Read: " + rulesString );
		return readRulesFromStream( is );
	}


	public String readRules( String fileName ) throws IOException, SAXException {
// update to read an XML file.
		String line, rules = "";
// System.out.println( "Reading Rules File: " + fileName );
/*
		BufferedReader ruleFile = new BufferedReader(  new InputStreamReader(new FileInputStream( fileName.toString() ), "UTF-8"));
		while ( (line = ruleFile.readLine()) != null) {
			rules += line;
		}
*/
		byte[] encoded = Files.readAllBytes(Paths.get(fileName));
		rules = new String(encoded, "UTF-8");
// System.out.println( "Rule File Read: " + rules);
		  
		return readRulesStringXML( rules );
	}


	/*
	 * Expected Args:
	 *
	 * 	xlit -reverse translit.xml inputFile outputFile
	 *
	 *           * -reverse is optional, if absent the forward direction is assumed.
	 *	     * inputFile is optional, STDIN will read if absent.
	 *	     * outputFile is optional, STDOUT will be written to if absent.
	 *
	 *	     If the inputFile argument is absent, and outputFile argument is given, then the outputFile is assumed to be the inputFile.
	 *
	 */
	private void processCommandLineArguments(String[] args) throws IOException, FileNotFoundException, UnsupportedEncodingException, SAXException {
		// parse arguments:
		for(String arg: args) {
			if ( arg.startsWith( "-reverse" ) ) {
				direction = Transliterator.REVERSE ;
System.out.println( "Set Reverse" );
			}
			else if ( arg.startsWith( "-" ) ) {
				System.err.println( "Unknown argument: " + arg );
			}
			else if ( this.xrules == null ) {
				// xrules = readRules( new File( arg ) );
				xrules = readRules( arg );
				// System.out.println( "Extracted Rules: " + xrules );
				transliterator = Transliterator.createFromRules( "In-Out", xrules.replace( '\ufeff', ' ' ), direction );
				Transliterator.registerInstance( transliterator );
			}
			else if ( this.inputFile == null ) {
				inputFile = arg;
				inStream = new BufferedReader( new FileReader( inputFile ) );
			}
			else if ( this.outputFile == null ) {
				outputFile = arg;
				outStream = new BufferedWriter(
						 new OutputStreamWriter(
								 new FileOutputStream( outputFile ),
								 "UTF8"
						)
				);
			}
		}
		
		if ( inStream == null ) {
			inStream = new BufferedReader( new InputStreamReader( System.in ) );
		}
		if ( outStream == null ) {
			outStream  = new BufferedWriter( new OutputStreamWriter( System.out ) );
		}
	}
	
	
/*
	protected void finalize() throws IOException {
		inStream.close();
		outStream.close();
	}
*/
	
	void transliterate() throws IOException {
		String line;
		while ( (line = inStream.readLine()) != null) {
			outStream.write( transliterator.transliterate( line ) );
			outStream.newLine();
		}
		outStream.flush();
	}
	
	
	/*****************************************************************\
	 *
	 * This application needs the ICU Java Library, downloadable from:
	 *
	 *     http://www.icu-project.org/download/
	 *
	 *  Compile:
	 *
	 *     javac -cp icu4j-4_0.jar org/geez/util/Translit.java 
	 *
	 *  Convert Ethiopic to Latin:
	 *
	 *     java -cp ".:icu4j-4_0.jar" org.geez.util.Translit QineAmharic.txt
	 *
	 *  Convert Latin to Ethiopic:
	 *
	 *     java -cp ".:icu4j-4_0.jar" org.geez.util.Translit -fromLatin QineLatin.txt
	 *
	 \*****************************************************************/

	public static void main(String[] args) {
		try {
			xlit x = new xlit( args );
			x.transliterate();
		  }
		  catch(Exception ex) {
			  System.out.println( ex );
		  }
	}

}
