package com.dooboolab.RNIap;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ObjectAlreadyConsumedException;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.text.NumberFormat;
import java.text.ParseException;

import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.PurchasingListener;

import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductType;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.RequestId;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;
import com.amazon.device.iap.model.FulfillmentResult;

public class RNIapAmazonModule extends ReactContextBaseJavaModule {
  final String TAG = "RNIapAmazonModule";

  private ReactContext reactContext;
  private HashMap<String, ArrayList<Promise>> promises = new HashMap<>();
  private List<Product> skus;

  private PurchasingListener purchasingListener = new PurchasingListener() {
    //final String TAG = "RNIapAmazonModule:listener";
    @Override
    public void onProductDataResponse(final ProductDataResponse response) {
      final ProductDataResponse.RequestStatus status = response.getRequestStatus();
      final String requestId = response.getRequestId().toString();
      //Log.d(TAG, "onProductDataResponse: (" + status + ")");
      Log.d(TAG, "onProductDataResponse: " + requestId + " (" + status + ")");

      switch(status) {
        case SUCCESSFUL:
          final Map<String, Product> productData = response.getProductData();
          final Set<String> unavailableSkus = response.getUnavailableSkus();

          WritableNativeArray items = new WritableNativeArray();

          NumberFormat format = NumberFormat.getCurrencyInstance();
          for (Map.Entry<String, Product> skuDetails : productData.entrySet()) {
            Product product = skuDetails.getValue();

            if (!skus.contains(product)) {
              skus.add(product);
            }

            ProductType productType = product.getProductType();
            final String productTypeString = (productType == ProductType.ENTITLED || productType == ProductType.CONSUMABLE) ? "inapp" : "subs";
            Number priceNumber = 0.00;
            try {
              String priceString = product.getPrice();
              if (priceString != null && !priceString.isEmpty()) {
                priceNumber = format.parse(priceString);
              }
            } catch (ParseException e) {
              Log.w(TAG, "onProductDataResponse: Failed to parse price for product: " + product.getSku());
            }

            WritableMap item = Arguments.createMap();
            final String sku = product.getSku();
            item.putString("productId", product.getSku());
            item.putString("price", priceNumber.toString());
            item.putString("currency", product.getPrice().substring(0, 1));
            item.putString("type", productTypeString);
            item.putString("localizedPrice", product.getPrice());
            item.putString("title", product.getTitle());
            item.putString("description", product.getDescription());
            item.putNull("introductoryPrice");
            item.putNull("subscriptionPeriodAndroid");
            item.putNull("freeTrialPeriodAndroid");
            item.putNull("introductoryPriceCyclesAndroid");
            item.putNull("introductoryPricePeriodAndroid");
            item.putString("iconUrl", product.getSmallIconUrl());
            item.putString("originalJson", product.toString());
            item.putString("originalPrice", product.getPrice());
            items.pushMap(item);
          }
          resolvePromises(requestId, items);
          break;
        case FAILED:
          rejectPromises(requestId, "failed", null);
          break;
        case NOT_SUPPORTED:
          rejectPromises(requestId, "not supported", null);
          break;
      }
    }

    @Override
    public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
      Log.d(TAG, "onPurchaseUpdatesResponse: requestId (" + response.getRequestId()
                   + ") purchaseUpdatesResponseStatus ("
                   + response.getRequestStatus()
                   + ") userId ("
                   + response.getUserData().getUserId()
                   + ")");
      final PurchaseUpdatesResponse.RequestStatus status = response.getRequestStatus();
    }

    @Override
    public void onPurchaseResponse(final PurchaseResponse response) {
      final String requestId = response.getRequestId().toString();
      final String userId = response.getUserData().getUserId();
      final PurchaseResponse.RequestStatus status = response.getRequestStatus();
      Log.d(TAG, "onPurchaseResponse: requestId (" + requestId
                   + ") userId ("
                   + userId
                   + ") purchaseRequestStatus ("
                   + status
                   + ")");
    }

    @Override
    public void onUserDataResponse(final UserDataResponse response) {
      Log.d(TAG, "onGetUserDataResponse: requestId (" + response.getRequestId()
                   + ") userIdRequestStatus: "
                   + response.getRequestStatus()
                   + ")");
      final UserDataResponse.RequestStatus status = response.getRequestStatus();
    }
  };

  public RNIapAmazonModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    this.skus = new ArrayList<Product>();
    // reactContext.addLifecycleEventListener(lifecycleEventListener);
    PurchasingService.registerListener(reactContext, purchasingListener);
  }

  @Override
  public String getName() {
    return TAG;
  }

  @ReactMethod
  public void initConnection(final Promise promise) {
    promise.resolve(true);
  }

  @ReactMethod
  public void getItemsByType(final String type, final ReadableArray skuArr, final Promise promise) {
    final Set <String>productSkus = new HashSet<String>();
    for (int ii = 0, skuSize = skuArr.size(); ii < skuSize; ii++) {
      productSkus.add(skuArr.getString(ii));
    }
    RequestId requestId = PurchasingService.getProductData(productSkus);
    savePromise(requestId.toString(), promise);
  }

  @ReactMethod
  public void startListening(final Promise promise) {
  }

  private void savePromise(String key, Promise promise) {
    // Log.d(TAG, "saving promise: " + key);
    ArrayList<Promise> list;
    if (promises.containsKey(key)) {
      list = promises.get(key);
    }
    else {
      list = new ArrayList<Promise>();
      promises.put(key, list);
    }

    list.add(promise);
  }

  private void resolvePromises(String key, Object value) {
    // Log.d(TAG, "resolving promises: " + key + " " + value);
    if (promises.containsKey(key)) {
      ArrayList<Promise> list = promises.get(key);
      for (Promise promise : list) {
        promise.resolve(value);
      }
      promises.remove(key);
    }
  }

  private void rejectPromises(String key, String message, Exception err) {
    // Log.d(TAG, "reject promises: " + key + " : " + message);
    if (promises.containsKey(key)) {
      ArrayList<Promise> list = promises.get(key);
      for (Promise promise : list) {
        promise.reject(message, err);
      }
      promises.remove(key);
    }
  }
}
