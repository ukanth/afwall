#/**
# * Contains shared programming interfaces.
# * All iptables "communication" is handled by this class.
# * 
# * Copyright (C) 2007-2008  The Android Open Source Project
# * Copyright (C) 2009-2011  Rodrigo Zechin Rosauro
# * Copyright (C) 2011-2012  Umakanthan Chandran
# *
# * This program is free software: you can redistribute it and/or modify
# * it under the terms of the GNU General Public License as published by
# * the Free Software Foundation, either version 3 of the License, or
# * (at your option) any later version.
# *
# * This program is distributed in the hope that it will be useful,
# * but WITHOUT ANY WARRANTY; without even the implied warranty of
# * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# * GNU General Public License for more details.
# *
# * You should have received a copy of the GNU General Public License
# * along with this program.  If not, see <http://www.gnu.org/licenses/>.
# *
# * @author Rodrigo Zechin Rosauro, Umakanthan Chandran
# * @version 1.1
# */

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := afwall
LOCAL_CERTIFICATE := platform

# Builds against the public SDK
# LOCAL_SDK_VERSION := current

include $(BUILD_PACKAGE)

# This finds and builds the test apk as well, so a single make does both.
include $(call all-makefiles-under,$(LOCAL_PATH))