/*
 * Copyright (C) 2012 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.chainfire.libsuperuser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import dev.ukanth.ufirewall.BuildConfig;

/**
 * Thread utility class continuously reading from an InputStream
 */
public class StreamGobbler extends Thread {
	private String shell = null;
	private BufferedReader reader = null;
	private List<String> writer = null;
	
	/**
	 * StreamGobbler constructor
	 * 
	 * We use this class because shell STDOUT and STDERR should be read as quickly as 
	 * possible to prevent a deadlock from occurring, or Process.waitFor() never
	 * returning (as the buffer is full, pausing the native process
	 * 
	 * @param shell Name of the shell
	 * @param inputStream InputStream to read from
	 * @param outputList List<String> to write to, or null
	 */
	public StreamGobbler(String shell, InputStream inputStream, List<String> outputList) {
		this.shell = shell;
		reader = new BufferedReader(new InputStreamReader(inputStream));
		writer = outputList;
	}
	
	@Override
	public void run() {
		// keep reading the InputStream until it ends (or an error occurs)
		try {
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (BuildConfig.DEBUG) {
					Debug.log(String.format("[%s] %s", shell, line));
				}
				if (writer != null) {
					writer.add(line);
				}
			}
		} catch (IOException e) {
		}
		
		// make sure our stream is closed and resources will be freed
		try {
			reader.close();
		} catch (IOException e) {			
		}
	}
}