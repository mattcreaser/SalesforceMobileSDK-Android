/*
 * Copyright (c) 2014-2015, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.store;

import java.io.File;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.security.Encryptor;
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper;

import net.sqlcipher.database.SQLiteDatabase;

import android.content.Context;
import android.os.Bundle;
import android.test.InstrumentationTestCase;

/**
 * Tests for obtaining and deleting databases via the DBOpenHelper.
 */
public class DBOpenHelperTest extends InstrumentationTestCase {

	// constants used for building a test user account
	private static final String TEST_USER_ID = "005123";
	private static final String TEST_ORG_ID = "00D123";
	private static final String TEST_COMMUNITY_ID = "cid123";

	private Context targetContext;
	private static final String TEST_SOUP = "test_soup";
	private static final String TEST_SOUP_2 = "test_soup_2";
	private static final String TEST_DB = "test_db";
	private static final String PASSCODE = Encryptor.hash("test_key", "hashing-key");

	@Override
	protected void setUp() throws Exception {
		this.targetContext = getInstrumentation().getTargetContext();

		// Delete external blobs folder for test db and test soup
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		helper.removeExternalBlobsDirectory(TEST_SOUP);

		super.setUp();
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}

	/**
	 * Make sure database name is correct for no account and no communityId.
	 */
	public void testGetHelperForNullAccountNullCommunityId() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertEquals("Database name is not correct.","somedb.db", dbName);
	}

	/**
	 * Make sure database name is correct for account without a communityId.
	 */
	public void testGetHelperForAccountNullCommunityId() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "somedb", testAcct, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
		assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
		assertTrue("Database name does not have default internal community id.",dbName.contains(UserAccount.INTERNAL_COMMUNITY_PATH));
	}

	/**
	 * Ensure that helpers are cached.
	 */
	public void testGetHelperIsCached() {
		DBOpenHelper helper1 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext, "somedb", null, null);
		assertSame("Helpers should be cached.", helper1, helper2);
	}

	/**
	 * When a database name is not given, the default name should be used.
	 */
	public void testGetHelperUsesDefaultDatabaseName() {
		UserAccount testAcct = getTestUserAccount();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		assertTrue("Database name is not correct.", dbName.startsWith(DBOpenHelper.DEFAULT_DB_NAME));
		assertTrue("Database name does not contain org id.",dbName.contains(TEST_ORG_ID));
		assertTrue("Database name does not contain user id.",dbName.contains(TEST_USER_ID));
		assertTrue("Database name does not have default internal community id.",dbName.contains(TEST_COMMUNITY_ID));
	}

	/**
	 * Ensures the default database is removed correctly.
	 */
	public void testDeleteDatabaseDefault() {

		// create db
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		DBOpenHelper.deleteDatabase(targetContext, null);
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
	}

	/**
	 * Ensures all user databases are removed correctly, and the
	 * global databases are retained.
	 */
	public void testDeleteAllUserDatabases() {
		final UserAccount testAcct = getTestUserAccount();

		// Create a user database we want to ensure is deleted.
		final DBOpenHelper helper1 = DBOpenHelper.getOpenHelper(targetContext,
				testAcct, TEST_COMMUNITY_ID);
		final SQLiteDatabase db1 = helper1.getWritableDatabase("");
		final String dbName1 = getBaseName(db1);

		// Create another user database we want to ensure is deleted.
		final DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext,
				testAcct, "other_community_id");
		final SQLiteDatabase db2 = helper2.getWritableDatabase("");
		final String dbName2 = getBaseName(db2);

		// Create a global database we want to ensure is not deleted.
		final DBOpenHelper helper3 = DBOpenHelper.getOpenHelper(targetContext, null);
		final SQLiteDatabase db3 = helper3.getWritableDatabase("");
		final String dbName3 = getBaseName(db3);

		// Delete all user databases.
		DBOpenHelper.deleteAllUserDatabases(targetContext);
		assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName1));
		assertFalse("Database should have been deleted.",
				databaseExists(targetContext, dbName2));
		assertTrue("Database should not have been deleted.",
				databaseExists(targetContext, dbName3));

		// 1st and 2nd helpers should not be cached, but 3rd should still be cached.
		final Map<String, DBOpenHelper> helpers = DBOpenHelper.getOpenHelpers();
		assertNotNull("List of helpers should not be null.", helpers);
		final Set<String> dbNames = helpers.keySet();
		assertNotNull("List of DB names should not be null.", dbNames);
		assertFalse("User database should not be cached.", dbNames.contains(dbName1));
		assertFalse("User database should not be cached.", dbNames.contains(dbName2));
		assertTrue("Global database should still be cached.", dbNames.contains(dbName3));
	}

	/**
	 * Ensures the database is removed from the cache.
	 */
	public void testDeleteDatabaseRemovesFromCache() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, null);
		DBOpenHelper.deleteDatabase(targetContext, null);
		DBOpenHelper helperPostDelete = DBOpenHelper.getOpenHelper(targetContext, null);
		assertNotSame("Helpers should be different instances.", helper, helperPostDelete);
	}

	/**
	 * Ensure that only the single community-specific database is deleted.
	 */
	public void testDeleteDatabaseWithCommunityId() {
		UserAccount testAcct = getTestUserAccount();

		// create the database we want to ensure is deleted
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		helper.getWritableDatabase("");
		
		// create another database for this account which should not be deleted.
		DBOpenHelper dontDeleteHelper = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		SQLiteDatabase dontDeleteDb = dontDeleteHelper.getWritableDatabase("");
		String dontDeleteDbName = getBaseName(dontDeleteDb);

		// now, delete the first database and make sure we didn't remove the second.
		DBOpenHelper.deleteDatabase(targetContext,  testAcct, TEST_COMMUNITY_ID);
		assertTrue("Database should not have been deleted.", databaseExists(targetContext, dontDeleteDbName));
		
		// 1st helper should not be cached, but 2nd should still be cached
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		assertNotSame("Helper should have been removed from cache.", helper, helperNew);
		DBOpenHelper dontDeleteHelperCached = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		assertSame("Helper should be same instance.", dontDeleteHelper, dontDeleteHelperCached);
	}

	/**
	 * Ensure that all databases related to the account are removed when community id is not specified.
	 */
	public void testDeleteDatabaseWithoutCommunityId() {
		UserAccount testAcct = getTestUserAccount();

		// create the database we want to ensure is deleted
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		SQLiteDatabase db = helper.getWritableDatabase("");
		String dbName = getBaseName(db);
		
		// create another database for this account which should not be deleted.
		DBOpenHelper helper2 = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		SQLiteDatabase db2 = helper2.getWritableDatabase("");
		String dbName2 = getBaseName(db2);

		// now, delete all databases related to accounts and ensure they no longer exist
		DBOpenHelper.deleteDatabase(targetContext,  testAcct);
		
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName));
		assertFalse("Database should not exist.", databaseExists(targetContext, dbName2));
		
		// also make sure references to the helpers no longer exist
		DBOpenHelper helperNew = DBOpenHelper.getOpenHelper(targetContext, testAcct, TEST_COMMUNITY_ID);
		DBOpenHelper helperNew2 = DBOpenHelper.getOpenHelper(targetContext, testAcct, "other_community_id");
		assertNotSame("Helper should have been removed from cache.", helper, helperNew);
		assertNotSame("Helper should have been removed from cache.", helper2, helperNew2);
	}

	/**
	 * Has smart store for given account returns true.
	 */
	public void testHasSmartStoreIsTrueForDefaultDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, testAcct);
		helper.getWritableDatabase("");
		assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
	public void testHasSmartStoreIsFalseForDefaultDatabase() {
		assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, null, null));
	}

	/**
	 * Has smart store for specified database and account returns true.
	 */
	public void testHasSmartStoreIsTrueForSpecifiedDatabase() {
		UserAccount testAcct = getTestUserAccount(); 
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "testdb", testAcct, null);
		helper.getWritableDatabase("");
		assertTrue("SmartStore for account should exist.",
				DBOpenHelper.smartStoreExists(targetContext, "testdb", testAcct, null));
	}

	/**
	 * Has smart store for given account returns false.
	 */
	public void testHasSmartStoreIsFalseForSpecifiedDatabase() {
		assertFalse("SmartStore for account should not exist.",
				DBOpenHelper.smartStoreExists(targetContext, "dbdne", null, null));
	}

	/**
	 * Ensures we get the expected soup blobs path
	 */
	public void testGetExternalSoupBlobsPath() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);

		// Test result when soup name is given
		assertTrue("Wrong external soup blobs path returned.",
					 helper.getExternalSoupBlobsPath(TEST_SOUP).endsWith("com.salesforce.androidsdk.smartstore.tests/databases/" + TEST_DB + ".db_external_soup_blobs/" + TEST_SOUP + "/"));

		// Test result when soup name is null
		assertTrue("Wrong external soup blobs path returned.",
					 helper.getExternalSoupBlobsPath(null).endsWith("com.salesforce.androidsdk.smartstore.tests/databases/" + TEST_DB + ".db_external_soup_blobs/"));
	}

	/**
	 * Ensures expected folder was created
	 */
	public void testCreateExternalBlobsDirectory() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		File folder = new File(targetContext.getApplicationInfo().dataDir + "/databases/" + TEST_DB + ".db_external_soup_blobs/" + TEST_SOUP + "/");

		// Clean up if folder already exists
		if (folder.exists()) {
			folder.delete();
		}

		// Act
		boolean result = helper.createExternalBlobsDirectory(TEST_SOUP);

		// Test
		assertTrue("Create operation was not successful", result);
		assertTrue("Folder for external blobs was not created.", folder.exists());

		// Clean up
		folder.delete();
	}

	/**
	 * Test correct size of entire blobs directory is given
	 */
	public void testGetSizeOfDir() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		String contents = "{size:9}";
		String encryptedContents = Encryptor.hash(contents, PASSCODE);

		// Create first subdirectory
		helper.createExternalBlobsDirectory(TEST_SOUP);
		long soupEntryId = 0;
		for (int i = 0; i < 100; i++) {
			helper.saveSoupBlob(TEST_SOUP, soupEntryId++, new JSONObject(contents), PASSCODE);
		}

		// Create second subdirectory
		helper.createExternalBlobsDirectory(TEST_SOUP_2);
		soupEntryId = 0;
		for (int i = 0; i < 100; i++) {
			helper.saveSoupBlob(TEST_SOUP_2, soupEntryId++, new JSONObject(contents), PASSCODE);
		}

		// Total size of all files should be 2 (since two subdirs) * 100 (since 100 files each) * filesize of each file after encryption
		assertEquals("Total file sizes of both subdirectories is not correct.", 2 * 100 * (encryptedContents.length() + 1), helper.getSizeOfDir(null));
	}

	/**
	 * Test size of entire blobs directory if it doesnt exist
	 */
	public void testGetSizeOfDirDoesntExist() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		DBOpenHelper.deleteDatabase(targetContext, TEST_DB, null, null);

		// Total size of directory that doesnt exist should be 0.
		assertEquals("Total file size should be zero if directory doesnt exist", 0, helper.getSizeOfDir(null));
	}

	/**
	 * Ensures files and all subdirs are removed
	 */
	public void testRemoveAllFiles() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		String contents = "{size:9}";

		// Create subdirectory and tons of files
		helper.createExternalBlobsDirectory(TEST_SOUP);
		long soupEntryId = 0;
		for (int i = 0; i < 100; i++) {
			helper.saveSoupBlob(TEST_SOUP, soupEntryId++, new JSONObject(contents), PASSCODE);
		}

		// Act
		DBOpenHelper.removeAllFiles(new File(helper.getExternalSoupBlobsPath(null)));

		// Test that external blobs folder was removed (it cannot be removed unless all subdirectories/files have been removed
		File folder = new File(targetContext.getApplicationInfo().dataDir + "/databases/" + TEST_DB + ".db_external_soup_blobs/");
		assertFalse("Directory must be removed after calling removeAllFiles.", folder.exists());
	}

	/**
	 * Ensures external blobs directory was removed
	 */
	public void testRemoveExternalBlobsDirectory() {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		File folder = new File(targetContext.getApplicationInfo().dataDir + "/databases/" + TEST_DB + ".db_external_soup_blobs/" + TEST_SOUP + "/");

		// Create external blobs dir
		helper.createExternalBlobsDirectory(TEST_SOUP);
		assertTrue("Folder for external blobs was not created.", folder.exists());

		// Act - delete external blobs dir
		boolean result = helper.removeExternalBlobsDirectory(TEST_SOUP);

		// Test
		assertTrue("Remove operation was not successful", result);
		assertFalse("Folder for external blobs was not removed.", folder.exists());
	}

	/**
	 * Ensures error is not thrown if dataDir is null
	 */
	public void testRemoveExternalBlobsDirectoryNullDataDir() {
		String realDataDir = targetContext.getApplicationInfo().dataDir;
		targetContext.getApplicationInfo().dataDir = null;
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, "test_db_null_datadir", null, null);

		// Act - attempt to delete external blobs dir with null dataDir
		boolean result = helper.removeExternalBlobsDirectory(TEST_SOUP);

		// Test
		assertFalse("Remove operation was not successful since dataDir was null", result);

		// Reset dataDir back to real value (calling getOpenHelper with a new db resets the dataDir)
		targetContext.getApplicationInfo().dataDir = realDataDir;
		DBOpenHelper.getOpenHelper(targetContext, "some_uncached_helper", null, null);
	}

	/**
	 * Ensures soup was created and stored on the file system.
	 */
	public void testSaveSoupBlob() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		helper.createExternalBlobsDirectory(TEST_SOUP);
		long soupEntryId = System.currentTimeMillis();
		JSONObject soupElt = new JSONObject("{test:true}");

		// Act
		helper.saveSoupBlob(TEST_SOUP, soupEntryId, soupElt, PASSCODE);

		// Verify file was created
		File blobFile = new File(helper.getExternalSoupBlobsPath(TEST_SOUP), "soupelt_" + soupEntryId);
		assertTrue("File for blob not found on storage", blobFile.exists());

		// Clean up
		blobFile.delete();
	}

	/**
	 * Ensures soup was successfully retrieved from file system
	 */
	public void testLoadSoupBlob() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		helper.createExternalBlobsDirectory(TEST_SOUP);
		long soupEntryId = System.currentTimeMillis();
		JSONObject soupElt = new JSONObject("{testKey:" + soupEntryId + "}");

		// First place blob on file system
		helper.saveSoupBlob(TEST_SOUP, soupEntryId, soupElt, PASSCODE);

		// Act
		JSONObject result = helper.loadSoupBlob(TEST_SOUP, soupEntryId, PASSCODE);

		// Verify
		assertTrue("Retrieved soup does not have expected keys.", result.has("testKey"));
		assertEquals("Retrieved soup does not have expected values.", soupEntryId, result.getLong("testKey"));

		// Clean up
		File blobFile = new File(helper.getExternalSoupBlobsPath(TEST_SOUP), "soupelt_" + soupEntryId);
		blobFile.delete();
	}
	/**
	 * Ensures soup was successfully removed from file system
	 */
	public void testRemoveSoupBlob() throws JSONException {
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);
		helper.createExternalBlobsDirectory(TEST_SOUP);
		long soupEntryId = System.currentTimeMillis();
		JSONObject soupElt = new JSONObject("{testKey:" + soupEntryId + "}");

		// First place blob on file system
		helper.saveSoupBlob(TEST_SOUP, soupEntryId, soupElt, PASSCODE);

		// Act
		helper.removeSoupBlob(TEST_SOUP, new Long[] { soupEntryId });

		// Verify
		File blobFile = new File(helper.getExternalSoupBlobsPath(TEST_SOUP), "soupelt_" + soupEntryId);
		assertFalse("File containing blob was not removed from file storage.", blobFile.exists());
	}

	/**
	 * Ensures expected folder was created
	 */
	public void testGetSoupBlobFile() {
		long soupEntryId = System.currentTimeMillis();
		DBOpenHelper helper = DBOpenHelper.getOpenHelper(targetContext, TEST_DB, null, null);

		// Act
		File soupBlobFile = helper.getSoupBlobFile(TEST_SOUP, soupEntryId);

		// Verify
		assertTrue("Soup blob file does not have expected path.",
					 soupBlobFile.getAbsolutePath().endsWith("com.salesforce.androidsdk.smartstore.tests/databases/" + TEST_DB + ".db_external_soup_blobs/" + TEST_SOUP + "/soupelt_" + soupEntryId));
	}

	/**
	 * Determines if the given database file exists or not in the database directory.
	 *
	 * @param dbName The database name.
	 * @return
	 */
	private boolean databaseExists(Context ctx, String dbName) {
		final String dbPath = ctx.getApplicationInfo().dataDir + "/databases/"  + dbName;
    	final File file = new File(dbPath);
		return file.exists();
	}

	/**
	 * Builds a user account we can use for test cases.
	 *
	 * @return A bare-bones user account.
	 */
	private UserAccount getTestUserAccount() {
		Bundle bundle = new Bundle();
		bundle.putString(UserAccount.USER_ID, TEST_USER_ID);
		bundle.putString(UserAccount.ORG_ID, TEST_ORG_ID);
		return new UserAccount(bundle);
	}

	/**
	 * Obtain the base filename from a given path.
	 *
	 * @param db The full path, including filename.
	 * @return Just the filename.
	 */
	private String getBaseName(SQLiteDatabase db) {
		final String[] pathParts = db.getPath().split("/");
		return pathParts[pathParts.length - 1];
	}
}
