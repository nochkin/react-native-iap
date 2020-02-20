package com.dooboolab.RNIap;

import androidx.annotation.Nullable;
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

  private static final String PROMISE_BUY_ITEM = "PROMISE_BUY_ITEM";
  private static final String PROMISE_GET_PRODUCT_DATA = "PROMISE_GET_PRODUCT_DATA";
  private static final String PROMISE_QUERY_PURCHASES = "PROMISE_QUERY_PURCHASES";

  private ReactContext reactContext;
  private List<Product> skus;

  private PurchasingListener purchasingListener = new PurchasingListener() {
    //final String TAG = "RNIapAmazonModule:listener";
    @Override
    public void onProductDataResponse(final ProductDataResponse response) {
      final ProductDataResponse.RequestStatus status = response.getRequestStatus();
      final String requestId = response.getRequestId().toString();
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
          DoobooUtils.getInstance().resolvePromisesForKey(PROMISE_GET_PRODUCT_DATA, items);
          break;
        case FAILED:
          DoobooUtils.getInstance().rejectPromisesForKey(PROMISE_GET_PRODUCT_DATA, null, null, null);
          break;
        case NOT_SUPPORTED:
          DoobooUtils.getInstance().rejectPromisesForKey(PROMISE_GET_PRODUCT_DATA, null, "not supported", null);
          break;
      }
    }

    @Override
    public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
      //Log.d(TAG, "onPurchaseUpdatesResponse: requestId (" + response.getRequestId()
      //             + ") purchaseUpdatesResponseStatus (" + response.getRequestStatus()
      //             + ") userId (" + response.getUserData().getUserId() + ")");
      Log.d(TAG, "onPurchaseUpdatesResponse: " + response.toString());
      final PurchaseUpdatesResponse.RequestStatus status = response.getRequestStatus();
      switch(status) {
        case SUCCESSFUL:
          ArrayList<Receipt> unacknowledgedPurchases = new ArrayList<>();
          List<Receipt> purchases = response.getReceipts();
          for (Receipt receipt : purchases) {
            unacknowledgedPurchases.add(receipt);
            Log.d(TAG, "receipt: " + receipt.toString());
            WritableMap item = Arguments.createMap();
            item.putString("productId", receipt.getSku());
            //item.putString("transactionId", purchase.getOrderId());
            item.putDouble("transactionDate", receipt.getPurchaseDate().getTime());
            //item.putString("transactionReceipt", purchase.getOriginalJson());
            item.putString("purchaseToken", receipt.getReceiptId());
            //item.putString("dataAndroid", purchase.getOriginalJson());
            //item.putString("signatureAndroid", purchase.getSignature());
            //item.putBoolean("autoRenewingAndroid", purchase.isAutoRenewing());
            //item.putBoolean("isAcknowledgedAndroid", purchase.isAcknowledged());
            //item.putInt("purchaseStateAndroid", purchase.getPurchaseState());
            item.putString("useridAmazon", response.getUserData().getUserId());
            item.putString("userMarketplaceAmazon", response.getUserData().getMarketplace());

            sendEvent(reactContext, "purchase-updated", item);
          }
          DoobooUtils.getInstance().resolvePromisesForKey(PROMISE_BUY_ITEM, unacknowledgedPurchases);
          DoobooUtils.getInstance().resolvePromisesForKey(PROMISE_QUERY_PURCHASES, true);
          break;
        case FAILED:
          DoobooUtils.getInstance().rejectPromisesForKey(PROMISE_QUERY_PURCHASES, null, null, null);
          break;
      }
    }

    @Override
    public void onPurchaseResponse(final PurchaseResponse response) {
      final String requestId = response.getRequestId().toString();
      final String userId = response.getUserData().getUserId();
      final PurchaseResponse.RequestStatus status = response.getRequestStatus();
      //Log.d(TAG, "onPurchaseResponse: requestId (" + requestId + ") userId ("
      //             + userId + ") purchaseRequestStatus (" + status + ")");
      Log.d(TAG, "onPurchaseResponse: " + response.toString());
      switch(status) {
        case SUCCESSFUL:
          Receipt receipt = response.getReceipt();
          WritableMap item = Arguments.createMap();
          item.putString("productId", receipt.getSku());
          item.putDouble("transactionDate", receipt.getPurchaseDate().getTime());
          item.putString("purchaseToken", receipt.getReceiptId());
          item.putString("useridAmazon", response.getUserData().getUserId());
          item.putString("userMarketplaceAmazon", response.getUserData().getMarketplace());
          sendEvent(reactContext, "purchase-updated", item);

          DoobooUtils.getInstance().resolvePromisesForKey(PROMISE_GET_PRODUCT_DATA, true);
          break;
        case FAILED:
          DoobooUtils.getInstance().rejectPromisesForKey(PROMISE_BUY_ITEM, null, null, null);
          break;
      }
    }

    @Override
    public void onUserDataResponse(final UserDataResponse response) {
      //Log.d(TAG, "onGetUserDataResponse: requestId (" + response.getRequestId()
      //             + ") userIdRequestStatus: " + response.getRequestStatus() + ")");
      Log.d(TAG, "onGetUserDataResponse: " + response.toString());
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
    DoobooUtils.getInstance().addPromiseForKey(PROMISE_GET_PRODUCT_DATA, promise);
  }

  @ReactMethod
  public void buyItemByType(
    final String type,
    final String sku,
    final String oldSku,
    final Integer prorationMode,
    final String developerId,
    final String accountId,
    final Promise promise
  ) {
    RequestId requestId = PurchasingService.purchase(sku);
    DoobooUtils.getInstance().addPromiseForKey(PROMISE_BUY_ITEM, promise);
  }

  @ReactMethod
  public void acknowledgePurchase(final String token, final String developerPayLoad, final Promise promise) {
    Log.d(TAG, "acknowledgePurchase " + token);
    PurchasingService.notifyFulfillment(token, FulfillmentResult.FULFILLED);
    promise.resolve(true);
  }

  @ReactMethod
  public void consumeProduct(final String token, final String developerPayLoad, final Promise promise) {
    Log.d(TAG, "consumeProduct " + token);
    PurchasingService.notifyFulfillment(token, FulfillmentResult.FULFILLED);
    promise.resolve(true);
  }

  private void sendUnconsumedPurchases(final Promise promise) {
    PurchasingService.getPurchaseUpdates(true);
    DoobooUtils.getInstance().addPromiseForKey(PROMISE_QUERY_PURCHASES, promise);
  }

  @ReactMethod
  public void startListening(final Promise promise) {
    sendUnconsumedPurchases(promise);
  }

  private void sendEvent(ReactContext reactContext,
                         String eventName,
                         @Nullable WritableMap params) {
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }
}
