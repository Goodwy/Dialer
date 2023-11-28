package com.goodwy.dialer.interfaces;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.goodwy.dialer.helpers.Converters;
import com.goodwy.dialer.models.Timer;
import com.goodwy.dialer.models.TimerState;
import java.lang.Class;
import java.lang.Integer;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TimerDao_Impl implements TimerDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Timer> __insertionAdapterOfTimer;

  private final Converters __converters = new Converters();

  private final EntityDeletionOrUpdateAdapter<Timer> __deletionAdapterOfTimer;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTimer;

  public TimerDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTimer = new EntityInsertionAdapter<Timer>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `timers` (`id`,`seconds`,`state`,`vibrate`,`soundUri`,`soundTitle`,`title`,`label`,`description`,`createdAt`,`channelId`,`oneShot`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Timer entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindLong(1, entity.getId());
        }
        statement.bindLong(2, entity.getSeconds());
        final String _tmp = __converters.timerStateToJson(entity.getState());
        statement.bindString(3, _tmp);
        final int _tmp_1 = entity.getVibrate() ? 1 : 0;
        statement.bindLong(4, _tmp_1);
        statement.bindString(5, entity.getSoundUri());
        statement.bindString(6, entity.getSoundTitle());
        statement.bindString(7, entity.getTitle());
        statement.bindString(8, entity.getLabel());
        statement.bindString(9, entity.getDescription());
        statement.bindLong(10, entity.getCreatedAt());
        if (entity.getChannelId() == null) {
          statement.bindNull(11);
        } else {
          statement.bindString(11, entity.getChannelId());
        }
        final int _tmp_2 = entity.getOneShot() ? 1 : 0;
        statement.bindLong(12, _tmp_2);
      }
    };
    this.__deletionAdapterOfTimer = new EntityDeletionOrUpdateAdapter<Timer>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `timers` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Timer entity) {
        if (entity.getId() == null) {
          statement.bindNull(1);
        } else {
          statement.bindLong(1, entity.getId());
        }
      }
    };
    this.__preparedStmtOfDeleteTimer = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM timers WHERE id=?";
        return _query;
      }
    };
  }

  @Override
  public long insertOrUpdateTimer(final Timer timer) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfTimer.insertAndReturnId(timer);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteTimers(final List<Timer> list) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfTimer.handleMultiple(list);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteTimer(final int id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTimer.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteTimer.release(_stmt);
    }
  }

  @Override
  public List<Timer> getTimers() {
    final String _sql = "SELECT * FROM timers ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "seconds");
      final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
      final int _cursorIndexOfVibrate = CursorUtil.getColumnIndexOrThrow(_cursor, "vibrate");
      final int _cursorIndexOfSoundUri = CursorUtil.getColumnIndexOrThrow(_cursor, "soundUri");
      final int _cursorIndexOfSoundTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "soundTitle");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfChannelId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelId");
      final int _cursorIndexOfOneShot = CursorUtil.getColumnIndexOrThrow(_cursor, "oneShot");
      final List<Timer> _result = new ArrayList<Timer>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Timer _item;
        final Integer _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getInt(_cursorIndexOfId);
        }
        final int _tmpSeconds;
        _tmpSeconds = _cursor.getInt(_cursorIndexOfSeconds);
        final TimerState _tmpState;
        final String _tmp;
        _tmp = _cursor.getString(_cursorIndexOfState);
        _tmpState = __converters.jsonToTimerState(_tmp);
        final boolean _tmpVibrate;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfVibrate);
        _tmpVibrate = _tmp_1 != 0;
        final String _tmpSoundUri;
        _tmpSoundUri = _cursor.getString(_cursorIndexOfSoundUri);
        final String _tmpSoundTitle;
        _tmpSoundTitle = _cursor.getString(_cursorIndexOfSoundTitle);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpLabel;
        _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
        final String _tmpDescription;
        _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final String _tmpChannelId;
        if (_cursor.isNull(_cursorIndexOfChannelId)) {
          _tmpChannelId = null;
        } else {
          _tmpChannelId = _cursor.getString(_cursorIndexOfChannelId);
        }
        final boolean _tmpOneShot;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfOneShot);
        _tmpOneShot = _tmp_2 != 0;
        _item = new Timer(_tmpId,_tmpSeconds,_tmpState,_tmpVibrate,_tmpSoundUri,_tmpSoundTitle,_tmpTitle,_tmpLabel,_tmpDescription,_tmpCreatedAt,_tmpChannelId,_tmpOneShot);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public Timer getTimer(final int id) {
    final String _sql = "SELECT * FROM timers WHERE id=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "seconds");
      final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
      final int _cursorIndexOfVibrate = CursorUtil.getColumnIndexOrThrow(_cursor, "vibrate");
      final int _cursorIndexOfSoundUri = CursorUtil.getColumnIndexOrThrow(_cursor, "soundUri");
      final int _cursorIndexOfSoundTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "soundTitle");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfChannelId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelId");
      final int _cursorIndexOfOneShot = CursorUtil.getColumnIndexOrThrow(_cursor, "oneShot");
      final Timer _result;
      if (_cursor.moveToFirst()) {
        final Integer _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getInt(_cursorIndexOfId);
        }
        final int _tmpSeconds;
        _tmpSeconds = _cursor.getInt(_cursorIndexOfSeconds);
        final TimerState _tmpState;
        final String _tmp;
        _tmp = _cursor.getString(_cursorIndexOfState);
        _tmpState = __converters.jsonToTimerState(_tmp);
        final boolean _tmpVibrate;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfVibrate);
        _tmpVibrate = _tmp_1 != 0;
        final String _tmpSoundUri;
        _tmpSoundUri = _cursor.getString(_cursorIndexOfSoundUri);
        final String _tmpSoundTitle;
        _tmpSoundTitle = _cursor.getString(_cursorIndexOfSoundTitle);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpLabel;
        _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
        final String _tmpDescription;
        _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final String _tmpChannelId;
        if (_cursor.isNull(_cursorIndexOfChannelId)) {
          _tmpChannelId = null;
        } else {
          _tmpChannelId = _cursor.getString(_cursorIndexOfChannelId);
        }
        final boolean _tmpOneShot;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfOneShot);
        _tmpOneShot = _tmp_2 != 0;
        _result = new Timer(_tmpId,_tmpSeconds,_tmpState,_tmpVibrate,_tmpSoundUri,_tmpSoundTitle,_tmpTitle,_tmpLabel,_tmpDescription,_tmpCreatedAt,_tmpChannelId,_tmpOneShot);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<Timer> findTimers(final int seconds, final String label) {
    final String _sql = "SELECT * FROM timers WHERE seconds=? AND label=?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, seconds);
    _argIndex = 2;
    _statement.bindString(_argIndex, label);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfSeconds = CursorUtil.getColumnIndexOrThrow(_cursor, "seconds");
      final int _cursorIndexOfState = CursorUtil.getColumnIndexOrThrow(_cursor, "state");
      final int _cursorIndexOfVibrate = CursorUtil.getColumnIndexOrThrow(_cursor, "vibrate");
      final int _cursorIndexOfSoundUri = CursorUtil.getColumnIndexOrThrow(_cursor, "soundUri");
      final int _cursorIndexOfSoundTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "soundTitle");
      final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
      final int _cursorIndexOfLabel = CursorUtil.getColumnIndexOrThrow(_cursor, "label");
      final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
      final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
      final int _cursorIndexOfChannelId = CursorUtil.getColumnIndexOrThrow(_cursor, "channelId");
      final int _cursorIndexOfOneShot = CursorUtil.getColumnIndexOrThrow(_cursor, "oneShot");
      final List<Timer> _result = new ArrayList<Timer>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final Timer _item;
        final Integer _tmpId;
        if (_cursor.isNull(_cursorIndexOfId)) {
          _tmpId = null;
        } else {
          _tmpId = _cursor.getInt(_cursorIndexOfId);
        }
        final int _tmpSeconds;
        _tmpSeconds = _cursor.getInt(_cursorIndexOfSeconds);
        final TimerState _tmpState;
        final String _tmp;
        _tmp = _cursor.getString(_cursorIndexOfState);
        _tmpState = __converters.jsonToTimerState(_tmp);
        final boolean _tmpVibrate;
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfVibrate);
        _tmpVibrate = _tmp_1 != 0;
        final String _tmpSoundUri;
        _tmpSoundUri = _cursor.getString(_cursorIndexOfSoundUri);
        final String _tmpSoundTitle;
        _tmpSoundTitle = _cursor.getString(_cursorIndexOfSoundTitle);
        final String _tmpTitle;
        _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
        final String _tmpLabel;
        _tmpLabel = _cursor.getString(_cursorIndexOfLabel);
        final String _tmpDescription;
        _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
        final long _tmpCreatedAt;
        _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
        final String _tmpChannelId;
        if (_cursor.isNull(_cursorIndexOfChannelId)) {
          _tmpChannelId = null;
        } else {
          _tmpChannelId = _cursor.getString(_cursorIndexOfChannelId);
        }
        final boolean _tmpOneShot;
        final int _tmp_2;
        _tmp_2 = _cursor.getInt(_cursorIndexOfOneShot);
        _tmpOneShot = _tmp_2 != 0;
        _item = new Timer(_tmpId,_tmpSeconds,_tmpState,_tmpVibrate,_tmpSoundUri,_tmpSoundTitle,_tmpTitle,_tmpLabel,_tmpDescription,_tmpCreatedAt,_tmpChannelId,_tmpOneShot);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
