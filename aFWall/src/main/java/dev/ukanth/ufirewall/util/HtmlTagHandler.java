package dev.ukanth.ufirewall.util;

import android.content.res.Resources;
import android.graphics.Color;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;

import org.xml.sax.ContentHandler;
import org.xml.sax.XMLReader;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Stack;

/**
 * The Java file HtmlTagHandler handles, ul, ol,li, code, br, dd tags<br/>
 * The handleTagList() method handles the list tags, and handling of code tag was pretty easy,
 * I just need to find the start of the code tag, I do this by adding a flag on the start of the tag Spannable.
 * SPAN_MARK_MARK and when the tag on end I just find the object where I marked Spannable.SPAN_MARK_MARK using getLast() and find its position,
 * and store it in the variable name "where"; that's the start of the code tag,
 * and I get the end of the code tag using output.length() and set the font face of that text fragment
 * to "monospace" using (new TypefaceSpan("monospace").
 * <p/>
 * Created by tasneem on 5/5/16.
 */


public class HtmlTagHandler implements Html.TagHandler {
    /**
     * Keeps track of lists (ol, ul). On bottom of Stack is the outermost list
     * and on top of Stack is the most nested list
     */
    Stack<String> lists = new Stack<>();
    /**
     * Tracks indexes of ordered lists so that after a nested list ends
     * we can continue with correct index of outer list
     */
    Stack<Integer> olNextIndex = new Stack<>();
    /**
     * List indentation in pixels. Nested lists use multiple of this.
     */
    /**
     * Running HTML table string based off of the root table tag. Root table tag being the tag which
     * isn't embedded within any other table tag. Example:
     * <!-- This is the root level opening table tag. This is where we keep track of tables. -->
     * <table>
     * ...
     * <table> <!-- Non-root table tags -->
     * ...
     * </table>
     * ...
     * </table>
     * <!-- This is the root level closing table tag and the end of the string we track. -->
     */
    StringBuilder tableHtmlBuilder = new StringBuilder();
    /**
     * Tells us which level of table tag we're on; ultimately used to find the root table tag.
     */
    int tableTagLevel = 0;

    private static final int indent = 10;
    private static final int listItemIndent = indent * 2;
    private static final BulletSpan bullet = new BulletSpan(indent);

    private static class Ul {
    }

    private static class Ol {
    }

    private static class Code {
    }

    private static class Center {
    }

    private static class Strike {
    }

    private static class Table {
    }

    private static class Tr {
    }

    private static class Th {
    }

    private static class Td {
    }

    private static class Font {

    }
    final HashMap<String, String> attributes = new HashMap<String, String>();

    private void processAttributes(final XMLReader xmlReader) {
        try {
            Field elementField = xmlReader.getClass().getDeclaredField("theNewElement");
            elementField.setAccessible(true);
            Object element = elementField.get(xmlReader);
            Field attsField = element.getClass().getDeclaredField("theAtts");
            attsField.setAccessible(true);
            Object atts = attsField.get(element);
            Field dataField = atts.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            String[] data = (String[])dataField.get(atts);
            Field lengthField = atts.getClass().getDeclaredField("length");
            lengthField.setAccessible(true);
            int len = (Integer)lengthField.get(atts);

            /**
             * MSH: Look for supported attributes and add to hash map.
             * This is as tight as things can get :)
             * The data index is "just" where the keys and values are stored.
             */
            for(int i = 0; i < len; i++)
                attributes.put(data[i * 5 + 1], data[i * 5 + 4]);
        }
        catch (Exception e) {
            Log.d("", "Exception: " + e);
        }
    }

    @Override
    public void handleTag(final boolean opening, final String tag, Editable output, final XMLReader xmlReader) {
        processAttributes(xmlReader);
        Log.d("handleTag", tag);
        Log.d("handleTag", "Opening : " + opening);

        if (opening) {
            // opening tag

            if (tag.equalsIgnoreCase("ul")) {
                lists.push(tag);
            } else if (tag.equalsIgnoreCase("ol")) {
                lists.push(tag);
                olNextIndex.push(1);
            } else if (tag.equalsIgnoreCase("li")) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                    output.append("\n");
                }
                String parentList = lists.peek();
                if (parentList.equalsIgnoreCase("ol")) {
                    start(output, new Ol());
                    output.append(olNextIndex.peek().toString()).append(". ");
                    olNextIndex.push(olNextIndex.pop() + 1);
                } else if (parentList.equalsIgnoreCase("ul")) {
                    start(output, new Ul());
                }
            } else if (tag.equalsIgnoreCase("code")) {
                start(output, new Code());
            } else if (tag.equalsIgnoreCase("center")) {
                start(output, new Center());
            } else if (tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                start(output, new Strike());
            } else if (tag.equalsIgnoreCase("table")) {
                start(output, new Table());
                if (tableTagLevel == 0) {
                    tableHtmlBuilder = new StringBuilder();
                    // We need some text for the table to be replaced by the span because
                    // the other tags will remove their text when their text is extracted
                    output.append("table placeholder");
                }

                tableTagLevel++;
            } else if (tag.equalsIgnoreCase("tr")) {
                start(output, new Tr());
            } else if (tag.equalsIgnoreCase("th")) {
                start(output, new Th());
            } else if (tag.equalsIgnoreCase("td")) {
                start(output, new Td());
            } else if (tag.equalsIgnoreCase("customFont")) {
                Log.d("HtmlTagHandler", "handeling font tag : "+output.toString());
                start(output, new Font());
            }
        } else {
            // closing tag
            if (tag.equalsIgnoreCase("ul")) {
                lists.pop();
            } else if (tag.equalsIgnoreCase("ol")) {
                lists.pop();
                olNextIndex.pop();
            } else if (tag.equalsIgnoreCase("li")) {
                if (lists.peek().equalsIgnoreCase("ul")) {
                    if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                        output.append("\n");
                    }
                    // Nested BulletSpans increases distance between bullet and text, so we must prevent it.
                    int bulletMargin = indent;
                    if (lists.size() > 1) {
                        bulletMargin = indent - bullet.getLeadingMargin(true);
                        if (lists.size() > 2) {
                            // This get's more complicated when we add a LeadingMarginSpan into the same line:
                            // we have also counter it's effect to BulletSpan
                            bulletMargin -= (lists.size() - 2) * listItemIndent;
                        }
                    }
                    BulletSpan newBullet = new BulletSpan(bulletMargin);
                    end(output, Ul.class, false,
                            new LeadingMarginSpan.Standard(listItemIndent * (lists.size() - 1)),
                            newBullet);
                } else if (lists.peek().equalsIgnoreCase("ol")) {
                    if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                        output.append("\n");
                    }
                    int numberMargin = listItemIndent * (lists.size() - 1);
                    if (lists.size() > 2) {
                        // Same as in ordered lists: counter the effect of nested Spans
                        numberMargin -= (lists.size() - 2) * listItemIndent;
                    }
                    end(output, Ol.class, false, new LeadingMarginSpan.Standard(numberMargin));
                }
            } else if (tag.equalsIgnoreCase("code")) {
                end(output, Code.class, false, new TypefaceSpan("monospace"));
            } else if (tag.equalsIgnoreCase("center")) {
                end(output, Center.class, true, new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER));
            } else if (tag.equalsIgnoreCase("s") || tag.equalsIgnoreCase("strike")) {
                end(output, Strike.class, false, new StrikethroughSpan());
            } else if (tag.equalsIgnoreCase("table")) {
                tableTagLevel--;

                // When we're back at the root-level table
                if (tableTagLevel == 0) {
                    final String tableHtml = tableHtmlBuilder.toString();


                } else {
                    end(output, Table.class, false);
                }
            } else if (tag.equalsIgnoreCase("tr")) {
                end(output, Tr.class, false);
            } else if (tag.equalsIgnoreCase("th")) {
                end(output, Th.class, false);
            } else if (tag.equalsIgnoreCase("td")) {
                end(output, Td.class, false);
            } else if (tag.equalsIgnoreCase("customFont")) {
                float size = 1f;
                if (attributes != null && attributes.size() > 0) {
                    if (attributes.containsKey("size")) {
                        Log.d("Attribute", attributes.get("size"));
                        size = Float.parseFloat(attributes.get("size"))/2;
                    }
                    if (attributes.containsKey("color")) {
                        Log.d("Attribute", attributes.get("color"));
                        if (attributes.get("color").startsWith("#")) {
                            end(output, Font.class, false, new RelativeSizeSpan(size), new ForegroundColorSpan(Color.parseColor(attributes.get("color"))));
                        }
                    } else {
                        end(output, Font.class, false, new RelativeSizeSpan(size));
                    }
                } else {
                    end(output, Font.class, false, new RelativeSizeSpan(size));
                }
            }
        }
        storeTableTags(opening, tag);
    }

    /**
     * If we're arriving at a table tag or are already within a table tag, then we should store it
     * the raw HTML for our ClickableTableSpan
     */
    private void storeTableTags(boolean opening, String tag) {
        if (tableTagLevel > 0 || tag.equalsIgnoreCase("table")) {
            tableHtmlBuilder.append("<");
            if (!opening) {
                tableHtmlBuilder.append("/");
            }
            tableHtmlBuilder
                    .append(tag.toLowerCase())
                    .append(">");
        }
    }

    /**
     * Mark the opening tag by using private classes
     */
    private void start(Editable output, Object mark) {
        int len = output.length();
        output.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK);


    }

    /**
     * Modified from {@link android.text.Html}
     */
    private void end(Editable output, Class kind, boolean paragraphStyle, Object... replaces) {
        Log.d("end output",output.toString());
        Object obj = getLast(output, kind);
        // start of the tag
        int where = output.getSpanStart(obj);
        // end of the tag
        int len = output.length();

        // If we're in a table, then we need to store the raw HTML for later
        if (tableTagLevel > 0) {
            final CharSequence extractedSpanText = extractSpanText(output, kind);
            tableHtmlBuilder.append(extractedSpanText);
        }

        output.removeSpan(obj);

        if (where != len) {
            int thisLen = len;
            // paragraph styles like AlignmentSpan need to end with a new line!
            if (paragraphStyle) {
                output.append("\n");
                thisLen++;
            }
            for (Object replace : replaces) {
                output.setSpan(replace, where, thisLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }


        }
    }

    /**
     * Returns the text contained within a span and deletes it from the output string
     */
    private CharSequence extractSpanText(Editable output, Class kind) {
        final Object obj = getLast(output, kind);
        // start of the tag
        final int where = output.getSpanStart(obj);
        // end of the tag
        final int len = output.length();

        final CharSequence extractedSpanText = output.subSequence(where, len);
        output.delete(where, len);
        return extractedSpanText;
    }

    /**
     * Get last marked position of a specific tag kind (private class)
     */
    private static Object getLast(Editable text, Class kind) {
        Object[] objs = text.getSpans(0, text.length(), kind);
        if (objs.length == 0) {
            return null;
        } else {
            for (int i = objs.length; i > 0; i--) {
                if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) {
                    return objs[i - 1];
                }
            }
            return null;
        }
    }

}