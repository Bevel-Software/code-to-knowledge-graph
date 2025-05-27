/**
 * Inmemantlr - In memory compiler for Antlr 4
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Julian Thome <julian.thome.de@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/

package org.snt.inmemantlr.tree;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonProcessor extends ParseTreeProcessor<StringBuilder, StringBuilder> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonProcessor.class);

    private boolean idxOnly = false;

    /**
     * constructor
     *
     * @param parseTree abstract syntax tree to process
     * @param idxOnly print index only
     */
    public JsonProcessor(ParseTree parseTree, boolean idxOnly) {
        super(parseTree);
        this.idxOnly = idxOnly;
    }

    /**
     * constructor
     *
     * @param parseTree abstract syntax tree to process
     */
    public JsonProcessor(ParseTree parseTree) {
        this(parseTree,true);
    }

    @Override
    public StringBuilder getResult() {
        return smap.get(parseTree.getRoot());
    }

    @Override
    protected void initialize() {

    }

    @Override
    protected void process(ParseTreeRule n) {

        StringBuilder sb = new StringBuilder();

        if(!n.hasParent()){
            simpleProp(n);
            return;
        }

        sb.append("{");
        sb.append("\"nt\":\"");
        sb.append(n.getRule());
        sb.append("\",\"ran\":\"");
        sb.append(n.getSidx());
        sb.append(",");
        sb.append(n.getEidx());
        sb.append("\"");

        if(!idxOnly) {
            sb.append("\",\"lbl\":\"");
            sb.append(n.getEscapedLabel());
            sb.append("\"");
        }

        if (n.hasChildren()) {
            sb.append(",");
            sb.append("\"cld\":[");
            for (int i = 0; i < n.getChildren().size(); i++) {
                if (i > 0)
                    sb.append(",");

                sb.append(smap.get(n.getChild(i)));
            }
            sb.append("]");
        }

        sb.append("}");

        smap.put(n, sb);
    }
}
