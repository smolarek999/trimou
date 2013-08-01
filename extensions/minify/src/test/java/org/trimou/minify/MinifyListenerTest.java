package org.trimou.minify;

import static org.junit.Assert.assertEquals;

import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;
import org.trimou.engine.MustacheEngine;
import org.trimou.engine.MustacheEngineBuilder;
import org.trimou.engine.config.Configuration;

import com.google.common.collect.ImmutableMap;
import com.googlecode.htmlcompressor.compressor.HtmlCompressor;

/**
 *
 * @author Martin Kouba
 */
public class MinifyListenerTest {

    @Test
    public void testDefaultHtmlListener() {
        MustacheEngine engine = MustacheEngineBuilder.newBuilder()
                .addMustacheListener(Minify.htmlListener()).build();
        assertEquals(
                "<html><body> <p>FOO</p> </body> </html>",
                engine.compileMustache("minify_html",
                        "<html><body>   <!-- My comment -->  <p>{{foo}}</p>  </body>\n  </html>")
                        .render(ImmutableMap.<String, Object> of("foo", "FOO")));
    }

    @Test
    public void testDefaultXmlListener() {
        MustacheEngine engine = MustacheEngineBuilder.newBuilder()
                .addMustacheListener(Minify.xmlListener()).build();
        assertEquals(
                "<foo><bar>Hey FOO!</bar></foo>",
                engine.compileMustache("minify_xml",
                        "<foo> <!-- My comment -->  <bar>Hey {{foo}}!</bar> \n\n </foo>")
                        .render(ImmutableMap.<String, Object> of("foo", "FOO")));
    }

    @Test
    public void testCustomizedHtmlListener() {

        String contents = "<html><body><!-- My comment --></body></html>";

        MustacheEngine engine = MustacheEngineBuilder
                .newBuilder()
                .addMustacheListener(
                        Minify.customListener(new HtmlCompressorMinifier() {
                            @Override
                            protected void initCompressor(
                                    HtmlCompressor compressor,
                                    Configuration configuration) {
                                compressor.setEnabled(false);
                            }
                        })).build();
        // Compressor is disabled
        assertEquals(contents,
                engine.compileMustache("minify_html_customized", contents)
                        .render(null));

        engine = MustacheEngineBuilder
                .newBuilder()
                .addMustacheListener(
                        Minify.customListener(new HtmlCompressorMinifier() {
                            @Override
                            protected boolean match(String mustacheName) {
                                return mustacheName.endsWith("html");
                            }
                        })).build();
        // Mustache name does not match
        assertEquals(contents,
                engine.compileMustache("minify_html_customized", contents)
                        .render(null));
    }

    @Test
    public void testCustomListener() {

        MustacheEngine engine = MustacheEngineBuilder
                .newBuilder()
                .addMustacheListener(
                        Minify.customListener(new AbstractMinifier() {

                            @Override
                            public Reader minify(String mustacheName,
                                    Reader mustacheContents) {
                                return mustacheName.endsWith("js") ? new StringReader(
                                        "") : mustacheContents;
                            }

                        })).build();

        assertEquals("", engine.compileMustache("mustache.js", "whatever")
                .render(null));
        assertEquals("<html>",
                engine.compileMustache("foo", "<html>").render(null));
    }

}
