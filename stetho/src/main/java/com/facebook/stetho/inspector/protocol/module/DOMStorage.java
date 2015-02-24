// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.stetho.inspector.protocol.module;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import com.facebook.stetho.inspector.console.CLog;
import com.facebook.stetho.inspector.domstorage.DOMStoragePeerManager;
import com.facebook.stetho.inspector.domstorage.SharedPreferencesHelper;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod;
import com.facebook.stetho.json.ObjectMapper;
import com.facebook.stetho.json.annotation.JsonProperty;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class DOMStorage implements ChromeDevtoolsDomain {
  private final Context mContext;
  private final DOMStoragePeerManager mDOMStoragePeerManager;
  private final ObjectMapper mObjectMapper = new ObjectMapper();

  public DOMStorage(Context context) {
    mContext = context;
    mDOMStoragePeerManager = new DOMStoragePeerManager(context);
  }

  @ChromeDevtoolsMethod
  public void enable(JsonRpcPeer peer, JSONObject params) {
    mDOMStoragePeerManager.addPeer(peer);
  }

  @ChromeDevtoolsMethod
  public void disable(JsonRpcPeer peer, JSONObject params) {
    mDOMStoragePeerManager.removePeer(peer);
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getDOMStorageItems(JsonRpcPeer peer, JSONObject params)
      throws JSONException {
    StorageId storage = mObjectMapper.convertValue(
        params.getJSONObject("storageId"),
        StorageId.class);

    ArrayList<List<String>> entries = new ArrayList<List<String>>();
    String prefTag = storage.securityOrigin;
    if (storage.isLocalStorage) {
      SharedPreferences prefs = mContext.getSharedPreferences(prefTag, Context.MODE_PRIVATE);
      for (Map.Entry<String, ?> prefsEntry : prefs.getAll().entrySet()) {
        ArrayList<String> entry = new ArrayList<String>(2);
        entry.add(prefsEntry.getKey());
        entry.add(SharedPreferencesHelper.valueToString(prefsEntry.getValue()));
        entries.add(entry);
      }
    }

    GetDOMStorageItemsResult result = new GetDOMStorageItemsResult();
    result.entries = entries;
    return result;
  }

  @ChromeDevtoolsMethod
  public void setDOMStorageItem(JsonRpcPeer peer, JSONObject params) throws JSONException {
    StorageId storage = mObjectMapper.convertValue(
        params.getJSONObject("storageId"),
        StorageId.class);
    String key = params.getString("key");
    String value = params.getString("value");

    if (storage.isLocalStorage) {
      SharedPreferences prefs = mContext.getSharedPreferences(
          storage.securityOrigin,
          Context.MODE_PRIVATE);
      Object existingValue = prefs.getAll().get(key);
      if (existingValue == null) {
        CLog.writeToConsole(
            mDOMStoragePeerManager,
            Console.MessageLevel.ERROR,
            Console.MessageSource.STORAGE,
            "Unsupported: cannot add new key " + key + " due to lack of type inference");
      } else {
        SharedPreferences.Editor editor = prefs.edit();
        try {
          assignByType(editor, key, SharedPreferencesHelper.valueFromString(value, existingValue));
          editor.apply();
        } catch (IllegalArgumentException e) {
          CLog.writeToConsole(
              mDOMStoragePeerManager,
              Console.MessageLevel.ERROR,
              Console.MessageSource.STORAGE,
              String.format(Locale.US,
                  "Type mismatch setting %s to %s (expected %s)",
                  key,
                  value,
                  existingValue.getClass().getSimpleName()));
        }
      }
    }
  }

  @ChromeDevtoolsMethod
  public void removeDOMStorageItem(JsonRpcPeer peer, JSONObject params) throws JSONException {
    StorageId storage = mObjectMapper.convertValue(
        params.getJSONObject("storageId"),
        StorageId.class);
    String key = params.getString("key");

    if (storage.isLocalStorage) {
      SharedPreferences prefs = mContext.getSharedPreferences(
          storage.securityOrigin,
          Context.MODE_PRIVATE);
      prefs.edit().remove(key).apply();
    }
  }

  private static void assignByType(
      SharedPreferences.Editor editor,
      String key,
      Object value)
      throws IllegalArgumentException {
    if (value instanceof Integer) {
      editor.putInt(key, (Integer)value);
    } else if (value instanceof Long) {
      editor.putLong(key, (Long)value);
    } else if (value instanceof Float) {
      editor.putFloat(key, (Float)value);
    } else if (value instanceof Boolean) {
      editor.putBoolean(key, (Boolean)value);
    } else if (value instanceof String) {
      editor.putString(key, (String)value);
    } else if (value instanceof Set) {
      putStringSet(editor, key, (Set<String>)value);
    } else {
      throw new IllegalArgumentException("Unsupported type=" + value.getClass().getName());
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private static void putStringSet(SharedPreferences.Editor editor, String key, Set<String> value) {
    editor.putStringSet(key, value);
  }

  public static class StorageId {
    @JsonProperty(required = true)
    public String securityOrigin;

    @JsonProperty(required = true)
    public boolean isLocalStorage;
  }

  private static class GetDOMStorageItemsResult implements JsonRpcResult {
    @JsonProperty(required = true)
    public List<List<String>> entries;
  }

  public static class DomStorageItemsClearedParams {
    @JsonProperty(required = true)
    public StorageId storageId;
  }

  public static class DomStorageItemRemovedParams {
    @JsonProperty(required = true)
    public StorageId storageId;

    @JsonProperty(required = true)
    public String key;
  }

  public static class DomStorageItemAddedParams {
    @JsonProperty(required = true)
    public StorageId storageId;

    @JsonProperty(required = true)
    public String key;

    @JsonProperty(required = true)
    public String newValue;
  }

  public static class DomStorageItemUpdatedParams {
    @JsonProperty(required = true)
    public StorageId storageId;

    @JsonProperty(required = true)
    public String key;

    @JsonProperty(required = true)
    public String oldValue;

    @JsonProperty(required = true)
    public String newValue;
  }
}
