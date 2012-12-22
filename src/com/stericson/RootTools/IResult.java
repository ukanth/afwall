/* 
 * This file is part of the RootTools Project: http://code.google.com/p/roottools/
 *  
 * Copyright (c) 2012 Stephen Erickson, Chris Ravenscroft, Dominik Schuermann, Adam Shanks
 *  
 * This code is dual-licensed under the terms of the Apache License Version 2.0 and
 * the terms of the General Public License (GPL) Version 2.
 * You may use this code according to either of these licenses as is most appropriate
 * for your project on a case-by-case basis.
 * 
 * The terms of each license can be found in the root directory of this project's repository as well as at:
 * 
 * * http://www.apache.org/licenses/LICENSE-2.0
 * * http://www.gnu.org/licenses/gpl-2.0.txt
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under these Licenses is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See each License for the specific language governing permissions and
 * limitations under that License.
 */

package com.stericson.RootTools;

import java.io.Serializable;

/**
 * Implement this interface and inject the resulting object
 * when invoking <code>sendShell</code>.
 * <code>RootTools</code> comes with a reference implementation:
 * <code>RootTools.Result</code>
 */
public interface IResult {
    public abstract void process(String line) throws Exception;
    public abstract void processError(String line) throws Exception;
    public abstract void onFailure(Exception ex);
    public abstract void onComplete(int diag);

    public IResult      setProcess(Process process);
    public Process      getProcess();
    public IResult      setData(Serializable data);
    public Serializable getData();
    public IResult      setError(int error);
    public int          getError();

}
