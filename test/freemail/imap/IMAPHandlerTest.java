/*
 * IMAPHandlerTest.java
 * This file is part of Freemail, copyright (C) 2011 Martin Nyhus
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package freemail.imap;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import utils.Utils;

import fakes.FakeSocket;
import fakes.NullAccountManager;
import freemail.AccountManager;
import freemail.FreemailAccount;

import junit.framework.TestCase;

public class IMAPHandlerTest extends TestCase {
	private static final String ACCOUNT_MANAGER_DIR = "account_manager_dir";
	private static final String ACCOUNT_DIR = "account_dir";

	private File accountManagerDir;
	private File accountDir;

	@Override
	public void setUp() {
		// Set up account manager directory
		accountManagerDir = new File(ACCOUNT_MANAGER_DIR);
		if(accountManagerDir.exists()) {
			System.out.println("WARNING: Account manager directory exists, deleting");
			Utils.delete(accountManagerDir);
		}

		if(!accountManagerDir.mkdir()) {
			System.out.println("WARNING: Could not create account manager directory, tests will probably fail");
		}

		// Set up account directory
		accountDir = new File(ACCOUNT_DIR);
		if(accountDir.exists()) {
			System.out.println("WARNING: Account directory exists, deleting");
			Utils.delete(accountDir);
		}

		if(!accountDir.mkdir()) {
			System.out.println("WARNING: Could not create account directory, tests will probably fail");
		}
	}

	@Override
	public void tearDown() {
		Utils.delete(accountManagerDir);
		Utils.delete(accountDir);
	}

	/*
	 * This checks for the bug fixed in commit ad0b9aedf34f19ba7ed06757cdb53ca9d5614add.
	 * The IMAP thread would crash with a NullPointerException when receiving list with no arguments
	 */
	public void testIMAPListWithNoArguments() throws IOException {
		FakeSocket sock = new FakeSocket();
		AccountManager accManager = new ConfigurableAccountManager(accountManagerDir, false);

		Thread imapThread = new Thread(new IMAPHandler(accManager, sock));
		imapThread.start();

		PrintWriter toHandler = new PrintWriter(sock.getOutputStreamOtherSide());
		BufferedReader fromHandler = new BufferedReader(new InputStreamReader(sock.getInputStreamOtherSide()));

		fromHandler.readLine(); //Greeting

		send(toHandler, "0001 LOGIN test test\r\n");
		readTaggedResponse(fromHandler);

		send(toHandler, "0002 SELECT INBOX\r\n");
		readTaggedResponse(fromHandler);

		//This would crash the IMAP thread
		send(toHandler, "0003 LIST\r\n");

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {

		}

		//Check the state of the imap thread. Hopefully it will have had time to deal with the
		//command by now.
		assertFalse(imapThread.getState().equals(Thread.State.TERMINATED));
	}

	private static void send(PrintWriter out, String msg) {
		out.print(msg);
		out.flush();
	}

	private static String readTaggedResponse(BufferedReader in) throws IOException {
		String line = in.readLine();
		while(line.startsWith("*")) {
			line = in.readLine();
		}
		return line;
	}

	private class ConfigurableAccountManager extends NullAccountManager {
		private boolean failAuth;

		public ConfigurableAccountManager(File datadir, boolean failAuth) {
			super(datadir);

			this.failAuth = failAuth;
		}

		@Override
		public FreemailAccount authenticate(String username, String password) {
			if(failAuth) return null;

			//FreemailAccount constructor is package-protected and
			//there is no reason to change that, so use reflection
			//to construct a new account
			try {
				Class<FreemailAccount> freemailAccount = FreemailAccount.class;
				Constructor<FreemailAccount> constructor = freemailAccount.getDeclaredConstructor(String.class, File.class, PropsFile.class);
				constructor.setAccessible(true);
				return constructor.newInstance(username, accountDir, null);
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.toString());
			}

			return null;
		}
	}
}
