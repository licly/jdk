/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "cds/aotClassInitializer.hpp"
#include "cds/archiveBuilder.hpp"
#include "cds/cdsConfig.hpp"
#include "oops/instanceKlass.inline.hpp"

bool AOTClassInitializer::can_archive_initialized_mirror(InstanceKlass* ik) {
  assert(!ArchiveBuilder::current()->is_in_buffer_space(ik), "must be source klass");
  if (!CDSConfig::is_initing_classes_at_dump_time()) {
    return false;
  }

  if (ik->is_initialized() && ik->java_super() == vmClasses::Enum_klass()) {
    return true;
  } else if (ik->is_initialized() &&
             (ik->name()->equals("jdk/internal/constant/PrimitiveClassDescImpl") ||
              ik->name()->equals("jdk/internal/constant/ReferenceClassDescImpl") ||
              ik->name()->equals("java/lang/constant/ConstantDescs"))) {
    // The above classes are required to support the archival of sun.invoke.util.Wrapper
    // enums, which are referenced by archived java.lang.invoke.MethodType instances.
    return true;
  } else {
    return false;
  }
}
