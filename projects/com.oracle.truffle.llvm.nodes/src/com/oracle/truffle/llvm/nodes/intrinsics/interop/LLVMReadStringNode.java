/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.nodes.intrinsics.interop;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMReadStringNodeGen.ForeignReadStringNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNode.LLVMIncrementPointerNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAddressGetElementPtrNodeGen.LLVMIncrementPointerNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;

public abstract class LLVMReadStringNode extends Node {

    @Child PointerReadStringNode readOther;

    public abstract String executeWithTarget(VirtualFrame frame, Object address);

    @Specialization
    String readString(String address) {
        return address;
    }

    @Specialization
    String readForeign(VirtualFrame frame, LLVMTruffleObject foreign,
                    @Cached("create()") ForeignReadStringNode read) {
        return read.execute(frame, foreign);
    }

    @Fallback
    String readOther(VirtualFrame frame, Object address) {
        if (readOther == null) {
            readOther = insert(PointerReadStringNode.create());
        }
        return readOther.readPointer(frame, address);
    }

    abstract static class ForeignReadStringNode extends Node {

        @Child private Node isBoxed = Message.IS_BOXED.createNode();

        protected abstract String execute(VirtualFrame frame, LLVMTruffleObject foreign);

        @Specialization(guards = "isBoxed(object)")
        String readUnbox(LLVMTruffleObject object,
                        @Cached("createUnbox()") Node unbox) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, object.getObject());
                return (String) unboxed;
            } catch (UnsupportedMessageException ex) {
                throw ex.raise();
            }
        }

        @Specialization(guards = "!isBoxed(object)")
        String readOther(VirtualFrame frame, LLVMTruffleObject object,
                        @Cached("create()") PointerReadStringNode read) {
            return read.readPointer(frame, object);
        }

        protected boolean isBoxed(LLVMTruffleObject object) {
            return object.getOffset() == 0 && ForeignAccess.sendIsBoxed(isBoxed, object.getObject());
        }

        protected static Node createUnbox() {
            return Message.UNBOX.createNode();
        }

        public static ForeignReadStringNode create() {
            return ForeignReadStringNodeGen.create();
        }
    }

    static class PointerReadStringNode extends Node {

        @Child private LLVMIncrementPointerNode inc = LLVMIncrementPointerNodeGen.create();
        @Child private LLVMLoadNode read = LLVMI8LoadNodeGen.create();

        public String readPointer(VirtualFrame frame, Object address) {
            Object ptr = address;
            int length = 0;
            while ((byte) read.executeWithTarget(frame, ptr) != 0) {
                length++;
                ptr = inc.executeWithTarget(frame, ptr, Byte.BYTES);
            }

            char[] string = new char[length];

            ptr = address;
            for (int i = 0; i < length; i++) {
                string[i] = (char) Byte.toUnsignedInt((byte) read.executeWithTarget(frame, ptr));
                ptr = inc.executeWithTarget(frame, ptr, Byte.BYTES);
            }

            return toString(string);
        }

        @TruffleBoundary
        private static String toString(char[] string) {
            return new String(string);
        }

        public static PointerReadStringNode create() {
            return new PointerReadStringNode();
        }
    }
}
