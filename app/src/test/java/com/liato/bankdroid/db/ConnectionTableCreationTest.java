package com.liato.bankdroid.db;

import com.liato.bankdroid.banking.LegacyBankHelper;
import com.liato.bankdroid.banking.LegacyProviderConfiguration;
import com.liato.bankdroid.provider.IBankTypes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.IOException;

import static com.liato.bankdroid.db.Database.CONNECTION_PROVIDER_ID;
import static com.liato.bankdroid.db.Database.CONNECTION_TABLE_NAME;
import static com.liato.bankdroid.db.DatabaseTestHelper.withDatabaseVersion;
import static com.liato.bankdroid.db.DatabaseTestHelper.withEmptyDatabase;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConnectionTableCreationTest {

    private static final String PROVIDER_ID = LegacyBankHelper.getReferenceFromLegacyId(LegacyFixtures.LEGACY_BANK_TYPE);
    private static final int DISABLED = 0;
    private static final int INVALID_BANK_TYPE = -1;

    private DatabaseHelper underTest;
    private DatabaseTestHelper dbTestHelper;
    private SQLiteDatabase db;

    @Before
    public void setUp() throws IOException {
        underTest = DatabaseHelper.getHelper(RuntimeEnvironment.application);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void connection_table_is_created_when_onCreate_is_called() throws IOException {
        prepareDatabase(withEmptyDatabase());

        underTest.onCreate(db);
        assertThat("Connection table has not been created",
                dbTestHelper.tableExists(CONNECTION_TABLE_NAME),
                is(true));
    }

    @Test
    public void connection_table_is_created_on_db_upgrades_where_old_version_is_12()
            throws IOException {
        prepareDatabase(withDatabaseVersion(12));

        underTest.onUpgrade(db, 12, 13);
        assertThat("Connection table has not been created",
                dbTestHelper.tableExists(CONNECTION_TABLE_NAME),
                is(true));
    }

    @Test
    public void a_populated_bank_table_from_db_version_12_is_migrated_to_the_connection_table()
            throws IOException {
        prepareDatabase(withDatabaseVersion(12));

        db.insertOrThrow(LegacyDatabase.BANK_TABLE_NAME, null, LegacyFixtures.legacyBank());

        underTest.onUpgrade(db, 12, 13);

        assertThat(dbTestHelper.tableExists(LegacyDatabase.BANK_TABLE_NAME), is(false));

        Cursor actual = db.query(Database.CONNECTION_TABLE_NAME, null, null, null, null, null, null);
        assertThat(actual.getCount(), is(1));

        actual.moveToFirst();
        assertThat(actual.getLong(actual.getColumnIndex(Database.CONNECTION_ID)), is(LegacyFixtures.LEGACY_BANK_ID));
        assertThat(actual.getString(actual.getColumnIndex(Database.CONNECTION_NAME)), is(LegacyFixtures.LEGACY_BANK_CUSTOM_NAME));
        assertThat(actual.getString(actual.getColumnIndex(CONNECTION_PROVIDER_ID)), is(PROVIDER_ID));
        assertThat(actual.getInt(actual.getColumnIndex(Database.CONNECTION_ENABLED)), is(DISABLED));
        assertThat(actual.getString(actual.getColumnIndex(Database.CONNECTION_LAST_UPDATED)), is(LegacyFixtures.LEGACY_BANK_UPDATED));
        assertThat(actual.getInt(actual.getColumnIndex(Database.CONNECTION_SORT_ORDER)), is(LegacyFixtures.LEGACY_BANK_SORT_ORDER));
    }

    @Test
    public void a_bank_that_is_not_available_anymore_is_ignored_during_migration_to_v13()
            throws IOException {
        prepareDatabase(withDatabaseVersion(12));
        ContentValues legacyBankWithInvalidBankType = LegacyFixtures.legacyBank();
        legacyBankWithInvalidBankType.put(LegacyDatabase.BANK_TYPE, INVALID_BANK_TYPE);
        db.insertOrThrow(LegacyDatabase.BANK_TABLE_NAME, null, legacyBankWithInvalidBankType);

        underTest.onUpgrade(db, 12, 13);

        Cursor actual = db.query(Database.CONNECTION_TABLE_NAME, null, null, null, null, null, null);
        assertThat(actual.getCount(), is(0));

    }

    private void prepareDatabase(DatabaseTestHelper dbTestHelper) {
        this.dbTestHelper = dbTestHelper;
        this.db = dbTestHelper.db();
    }
}