package org.xwiki.rendering.scaffolding;

import junit.framework.TestSuite;

import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.PrintRenderer;
import com.xpn.xwiki.test.XWikiComponentInitializer;

public class RenderingTestSuite extends TestSuite
{
    private XWikiComponentInitializer initializer;

    private Map<String, PrintRendererFactory> rendererFactories;

    private class Data
    {
        public Map<Parser, String> inputs = new HashMap<Parser, String>();
        public Map<String, String> expectations = new HashMap<String, String>();
    }

    public RenderingTestSuite(String name, Map<String, PrintRendererFactory> rendererFactories) throws Exception
    {
        super(name);
        this.initializer = new XWikiComponentInitializer();
        this.initializer.initialize();
        this.rendererFactories = rendererFactories;
    }

    public void addTestsFromResource(String testResourceName, boolean runTransformations) throws Exception
    {
        String resourceName = "/" + testResourceName + ".test";
        Data data = readTestData(getClass().getResourceAsStream(resourceName), resourceName);

        // Create a test case for each input and for each expectation so that each test is executed separately
        // and reported separately by the JUnit test runner.
        for (Parser parser: data.inputs.keySet()) {
            for (String rendererId: data.expectations.keySet()) {
                PrintRenderer renderer = this.rendererFactories.get(rendererId).createRenderer();
                addTest(new RenderingTestCase(computeTestName(testResourceName, parser, renderer),
                    data.inputs.get(parser), data.expectations.get(rendererId), parser, renderer, runTransformations));
            }
        }
    }

    public Parser getParserFromString(String parserId) throws Exception
    {
        return (Parser) this.initializer.getComponentManager().lookup(Parser.ROLE, parserId);        
    }

    /**
     * Read test data separated by lines containing ".". For example:
     * <pre><code>
     * .input|xwiki/2.0
     * This is a test
     * .expect|XHTML
     * <p>This is a test</p>
     * </code></pre>
     */
    private Data readTestData(InputStream source, String resourceName) throws Exception
    {
        Data data = new Data();

        BufferedReader reader = new BufferedReader(new InputStreamReader(source));

        // Read each line and look for lines starting with ".". When this happens it means we've found a separate
        // test case.
        try {
            Map map = null;
            String keyName = null;
            boolean skip = false;
            StringBuffer buffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(".")) {
                    if (line.startsWith(".#")) {
                        // Ignore comments
                    } else {
                        // If there's already some data, write it to the maps now.
                        if (map != null) {
                            if (!skip) {
                                saveBuffer(buffer, map, data.inputs, keyName);
                            }
                            buffer.setLength(0);
                        }
                        // Parse the directive line starting with "." and with "|" separators.
                        // For example ".input|xwiki/2.0|skip" or ".expect|xhtml"
                        StringTokenizer st = new StringTokenizer(line.substring(1), "|");
                        // First token is "input" or "expect"
                        if (st.nextToken().equalsIgnoreCase("input")) {
                            map = data.inputs;
                        } else {
                            map = data.expectations;
                        }
                        // Second token is either the input syntax id or the expectation renderer short name
                        keyName = st.nextToken();
                        // Third (optional) token is whether the test should be skipped (useful while waiting for
                        // a fix to wikimodel for example).
                        skip = false;
                        if (st.hasMoreTokens()) {
                            skip = true;
                            System.out.println("[WARNING] Skipping test for [" + keyName + "] in file [" + resourceName
                                + "] since it has been marked as skipped in the test. This needs to be reviewed "
                                + "and fixed.");
                        }
                    }
                } else {
                    buffer.append(line).append('\n');
                }
            }

            if (!skip) {
                saveBuffer(buffer, map, data.inputs, keyName);
            }
            
        } finally {
            reader.close();
        }

        return data;
    }

    private void saveBuffer(StringBuffer buffer, Map map, Map<Parser, String> inputs, String keyName)
        throws Exception
    {
        // Remove the last newline since our test format forces an additional new lines
        // at the end of input texts.
        if (buffer.length() > 0 && buffer.charAt(buffer.length() - 1) == '\n') {
            buffer.setLength(buffer.length() - 1);
        }
        if (map == inputs) {
            map.put(getParserFromString(keyName), buffer.toString());
        } else {
            map.put(keyName, buffer.toString());
        }
    }

    private String computeTestName(String prefix, Parser parser, PrintRenderer renderer)
    {
        String parserName = parser.getClass().getName();
        String parserShortName = parserName.substring(parserName.lastIndexOf(".") + 1);

        String rendererName = renderer.getClass().getName();
        String rendererShortName = rendererName.substring(rendererName.lastIndexOf(".") + 1);

        return prefix + " (" + parserShortName + ", " + rendererShortName + ")";
    }
}
