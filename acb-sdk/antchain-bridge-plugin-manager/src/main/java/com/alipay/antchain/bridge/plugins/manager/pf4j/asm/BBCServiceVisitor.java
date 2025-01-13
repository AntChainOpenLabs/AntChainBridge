
/*
 * Copyright 2023 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.plugins.manager.pf4j.asm;

import java.util.Arrays;

import com.alipay.antchain.bridge.plugins.lib.BBCService;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This visitor extracts an {@link BBCServiceInfo} from any class,
 * that holds an {@link BBCService} annotation.
 * <p>
 * The annotation parameters are extracted from byte code by using the
 * <a href="https://asm.ow2.io/">ASM library</a>. This makes it possible to
 * access the {@link BBCService} parameters without loading the class into
 * the class loader. This avoids possible {@link NoClassDefFoundError}'s
 * for extensions, that can't be loaded due to missing dependencies.
 *
 */
class BBCServiceVisitor extends ClassVisitor {
    
    private static final Logger log = LoggerFactory.getLogger(BBCServiceVisitor.class);

    private static final int ASM_VERSION = Opcodes.ASM7;

    private final BBCServiceInfo BBCServiceInfo;

    BBCServiceVisitor(BBCServiceInfo BBCServiceInfo) {
        super(ASM_VERSION);
        this.BBCServiceInfo = BBCServiceInfo;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (!Type.getType(descriptor).getClassName().equals(BBCService.class.getName())) {
            return super.visitAnnotation(descriptor, visible);
        }

        return new AnnotationVisitor(ASM_VERSION) {

            @Override
            public AnnotationVisitor visitArray(final String name) {
                if ("products".equals(name)) {
                    return new AnnotationVisitor(ASM_VERSION, super.visitArray(name)) {

                        @Override
                        public void visit(String key, Object value) {
                            log.debug("Load annotation attribute {} = {} ({})", name, value, value.getClass().getName());
                            if ("products".equals(name)) {
                                if (value instanceof String) {
                                    log.debug("Found plugin {}", value);
                                    BBCServiceInfo.products.add((String) value);
                                } else if (value instanceof String[]) {
                                    log.debug("Found plugins {}", Arrays.toString((String[]) value));
                                    BBCServiceInfo.products.addAll(Arrays.asList((String[]) value));
                                } else {
                                    log.debug("Found plugin {}", value.toString());
                                    BBCServiceInfo.products.add(value.toString());
                                }
                            }

                            super.visit(key, value);
                        }
                    };
                }
                return super.visitArray(name);
            }

        };
    }

}
