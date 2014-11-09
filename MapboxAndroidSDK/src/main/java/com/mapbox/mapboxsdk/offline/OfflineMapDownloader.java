package com.mapbox.mapboxsdk.offline;

import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.constants.MathConstants;
import com.mapbox.mapboxsdk.geometry.CoordinateRegion;
import com.mapbox.mapboxsdk.util.DataLoadingUtils;
import com.mapbox.mapboxsdk.util.MapboxUtils;
import com.mapbox.mapboxsdk.util.NetworkUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;

public class OfflineMapDownloader implements MapboxConstants {

    private static final String TAG = "OfflineMapDownloader";

    private static OfflineMapDownloader offlineMapDownloader;

    private Context context;

    /**
     * The possible states of the offline map downloader.
     */
    enum MBXOfflineMapDownloaderState {
        /**
         * An offline map download job is in progress.
         */
        MBXOfflineMapDownloaderStateRunning,
        /**
         * An offline map download job is suspended and can be either resumed or canceled.
         */
        MBXOfflineMapDownloaderStateSuspended,
        /**
         * An offline map download job is being canceled.
         */
        MBXOfflineMapDownloaderStateCanceling,
        /**
         * The offline map downloader is ready to begin a new offline map download job.
         */
        MBXOfflineMapDownloaderStateAvailable
    }

    private String uniqueID;
    private String mapID;
    private boolean includesMetadata;
    private boolean includesMarkers;
    private RasterImageQuality imageQuality;
    private CoordinateRegion mapRegion;
    private int minimumZ;
    private int maximumZ;
    private MBXOfflineMapDownloaderState state;
    private int totalFilesWritten;
    private int totalFilesExpectedToWrite;


    private ArrayList<OfflineMapDatabase> mutableOfflineMapDatabases;

/*
    // Don't appear to be needed as there's one database per app for offline maps
    @property (nonatomic) NSString *partialDatabasePath;
    @property (nonatomic) NSURL *offlineMapDirectory;

    // Don't appear to be needed as as Android and Mapbox Android SDK provide these
    @property (nonatomic) NSOperationQueue *backgroundWorkQueue;
    @property (nonatomic) NSOperationQueue *sqliteQueue;
    @property (nonatomic) NSURLSession *dataSession;
    @property (nonatomic) NSInteger activeDataSessionTasks;
*/


    private OfflineMapDownloader(Context context)
    {
        super();
        this.context = context;

        mutableOfflineMapDatabases = new ArrayList<OfflineMapDatabase>();
    }

    public static OfflineMapDownloader getOfflineMapDownloader(Context context) {
        if (offlineMapDownloader == null)
        {
            offlineMapDownloader = new OfflineMapDownloader(context);
        }
        return offlineMapDownloader;
    }

/*
    API: Begin an offline map download
*/

    public void beginDownloadingMapID(String mapID, CoordinateRegion mapRegion, Integer minimumZ, Integer maximumZ)
    {
        beginDownloadingMapID(mapID, mapRegion, minimumZ, maximumZ, true, true, RasterImageQuality.MBXRasterImageQualityFull);
    }

    public void beginDownloadingMapID(String mapID, CoordinateRegion mapRegion, Integer minimumZ, Integer maximumZ, boolean includeMetadata, boolean includeMarkers)
    {
        beginDownloadingMapID(mapID, mapRegion, minimumZ, maximumZ, includeMetadata, includeMarkers, RasterImageQuality.MBXRasterImageQualityFull);
    }

    public void beginDownloadingMapID(String mapID, CoordinateRegion mapRegion, Integer minimumZ, Integer maximumZ, boolean includeMetadata, boolean includeMarkers, RasterImageQuality imageQuality)
    {
        if (state != MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateAvailable) {
            return;
        }

//        [self setUpNewDataSession];

//        [_backgroundWorkQueue addOperationWithBlock:^{

        // Start a download job to retrieve all the resources needed for using the specified map offline
        //
        this.uniqueID = UUID.randomUUID().toString();
        this.mapID = mapID;
        this.includesMetadata = includeMetadata;
        this.includesMarkers = includeMarkers;
        this.imageQuality = imageQuality;
        this.mapRegion = mapRegion;
        this.minimumZ = minimumZ;
        this.maximumZ = maximumZ;
        this.state = MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateRunning;
//        [self notifyDelegateOfStateChange];

        Hashtable<String, String> metadataDictionary = new Hashtable<String, String>();
        metadataDictionary.put("uniqueID", this.uniqueID);
        metadataDictionary.put("mapID", this.mapID);
        metadataDictionary.put("includesMetadata",  this.includesMetadata ? "YES" : "NO");
        metadataDictionary.put("includesMarkers", this.includesMarkers ? "YES" : "NO");
        metadataDictionary.put("imageQuality", String.format("%d", this.imageQuality.getValue()));
        metadataDictionary.put("region_latitude", String.format("%.8f", this.mapRegion.getCenter().getLatitude()));
        metadataDictionary.put("region_longitude", String.format("%.8f", this.mapRegion.getCenter().getLongitude()));
        metadataDictionary.put("region_latitude_delta", String.format("%.8f", this.mapRegion.getSpan().getLatitudeSpan()));
        metadataDictionary.put("region_longitude_delta", String.format("%.8f", this.mapRegion.getSpan().getLongitudeSpan()));
        metadataDictionary.put("minimumZ", String.format("%d", this.minimumZ));
        metadataDictionary.put("maximumZ", String.format("%d", this.maximumZ));

        final ArrayList<String> urls = new ArrayList<String>();

        String version = "v3";
        String dataName = "markers.geojson";    // Only using API v3 for now
//        NSString *dataName = ([MBXMapKit accessToken] ? @"features.json" : @"markers.geojson");
//        NSString *accessToken = ([MBXMapKit accessToken] ? [@"access_token=" stringByAppendingString:[MBXMapKit accessToken]] : nil);

        // Include URLs for the metadata and markers json if applicable
        //
        if(includeMetadata)
        {
            urls.add(String.format(MAPBOX_BASE_URL + "%s.json?secure%s", this.mapID, ""));
        }
        if(includeMarkers)
        {
            urls.add(String.format(MAPBOX_BASE_URL + "%s/%s%s", this.mapID, dataName, ""));
        }

        // Loop through the zoom levels and lat/lon bounds to generate a list of urls which should be included in the offline map
        //
        double minLat = this.mapRegion.getCenter().getLatitude() - (this.mapRegion.getSpan().getLatitudeSpan() / 2.0);
        double maxLat = minLat + this.mapRegion.getSpan().getLatitudeSpan();
        double minLon = this.mapRegion.getCenter().getLongitude() - (this.mapRegion.getSpan().getLongitudeSpan() / 2.0);
        double maxLon = minLon + this.mapRegion.getSpan().getLongitudeSpan();
        int minX;
        int maxX;
        int minY;
        int maxY;
        int tilesPerSide;
        for(int zoom = minimumZ; zoom <= maximumZ; zoom++)
        {
            tilesPerSide = new Double(Math.pow(2.0, zoom)).intValue();
            minX = new Double(Math.floor(((minLon + 180.0) / 360.0) * tilesPerSide)).intValue();
            maxX = new Double(Math.floor(((maxLon + 180.0) / 360.0) * tilesPerSide)).intValue();
            minY = new Double(Math.floor((1.0 - (Math.log(Math.tan(maxLat * MathConstants.PI / 180.0) + 1.0 / Math.cos(maxLat * MathConstants.PI / 180.0)) / MathConstants.PI)) / 2.0 * tilesPerSide)).intValue();
            maxY = new Double(Math.floor((1.0 - (Math.log(Math.tan(minLat * MathConstants.PI / 180.0) + 1.0 / Math.cos(minLat * MathConstants.PI / 180.0)) / MathConstants.PI)) / 2.0 * tilesPerSide)).intValue();
            for (int x = minX; x <= maxX; x++)
            {
                for (int y = minY; y <= maxY; y++)
                {
                    urls.add(String.format(MAPBOX_BASE_URL + "%s/%d/%d/%d%s.%s%s", this.mapID, zoom, x, y, "@2x", MapboxUtils.qualityExtensionForImageQuality(this.imageQuality), ""));
                }
            }
        }

        // Determine if we need to add marker icon urls (i.e. parse markers.geojson/features.json), and if so, add them
        //
        if(includeMarkers)
        {
            String dName = "markers.geojson";
            final String geojson = String.format(MAPBOX_BASE_URL + "%s/%s", this.mapID, dName);

            if (!NetworkUtils.isNetworkAvailable(context)) {
                // We got a session level error which probably indicates a connectivity problem such as airplane mode.
                // Since we must fetch and parse markers.geojson/features.json in order to determine which marker icons need to be
                // added to the list of urls to download, the lack of network connectivity is a non-recoverable error
                // here.
                //
                // TODO
/*
                [self notifyDelegateOfNetworkConnectivityError:error];
                [self cancelImmediatelyWithError:error];
*/
            }

            AsyncTask foo = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        HttpURLConnection conn = NetworkUtils.getHttpURLConnection(new URL(geojson));
                        conn.setConnectTimeout(60000);
                        conn.connect();
                        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                            throw new IOException();
                        }

                        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8")));
                        String jsonText = DataLoadingUtils.readAll(rd);

                        // The marker geojson was successfully retrieved, so parse it for marker icons. Note that we shouldn't
                        // try to save it here, because it may already be in the download queue and saving it twice will mess
                        // up the count of urls to be downloaded!
                        //
                        Set<String> markerIconURLStrings = parseMarkerIconURLStringsFromGeojsonData(jsonText);
                        if(markerIconURLStrings != null && markerIconURLStrings.size() > 0)
                        {
                            urls.addAll(markerIconURLStrings);
                        }
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        // The url for markers.geojson/features.json didn't work (some maps don't have any markers). Notify the delegate of the
                        // problem, and stop attempting to add marker icons, but don't bail out on whole the offline map download.
                        // The delegate can decide for itself whether it wants to continue or cancel.
                        //
                        // TODO
/*
                        [self notifyDelegateOfHTTPStatusError:((NSHTTPURLResponse *)response).statusCode url:response.URL];
*/
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);

                    // ==========================================================================================================
                    // == WARNING! WARNING! WARNING!                                                                           ==
                    // == This stuff is a duplicate of the code immediately below it, but this copy is inside of a completion  ==
                    // == block while the other isn't. You will be sad and confused if you try to eliminate the "duplication". ==
                    //===========================================================================================================

                    // Create the database and start the download
                    //

                    // TODO
/*
                    NSError *error;
                    [self sqliteCreateDatabaseUsingMetadata:metadataDictionary urlArray:urls withError:&error];
                    if(error)
                    {
                        [self cancelImmediatelyWithError:error];
                    }
                    else
                    {
                        [self notifyDelegateOfInitialCount];
                        [self startDownloading];
                    }
*/
                }
            };
            foo.execute();
        }
        else
        {
            // There aren't any marker icons to worry about, so just create database and start downloading
            //
            // TODO
/*
            NSError *error;
            [self sqliteCreateDatabaseUsingMetadata:metadataDictionary urlArray:urls withError:&error];
            if(error)
            {
                [self cancelImmediatelyWithError:error];
            }
            else
            {
                [self notifyDelegateOfInitialCount];
                [self startDownloading];
            }
*/
        }
    }

    public Set<String> parseMarkerIconURLStringsFromGeojsonData(String data)
    {
        HashSet<String> iconURLStrings = new HashSet<String>();

        JSONObject simplestyleJSONDictionary = null;
        try {
            simplestyleJSONDictionary = new JSONObject(data);

            // Find point features in the markers dictionary (if there are any) and add them to the map.
            //
            Object markers = simplestyleJSONDictionary.get("features");

            if (markers != null && markers instanceof JSONArray)
            {
                JSONArray array = (JSONArray)markers;

                for (int lc = 0; lc < array.length(); lc++)
                {
                    Object value = array.get(lc);
                    if (value instanceof JSONObject)
                    {
                        JSONObject feature = (JSONObject)value;
                        String type = feature.getJSONObject("geometry").getString("type");

                        if ("Point".equals(type))
                        {
                            String size        = feature.getJSONObject("properties").getString("marker-size");
                            String color       = feature.getJSONObject("properties").getString("marker-color");
                            String symbol      = feature.getJSONObject("properties").getString("marker-symbol");
                            if (!TextUtils.isEmpty(size) && !TextUtils.isEmpty(color) && !TextUtils.isEmpty(symbol))
                            {
                                String markerURL = MapboxUtils.markerIconURL(size, symbol, color);
                                if(!TextUtils.isEmpty(markerURL))
                                {
                                    iconURLStrings.add(markerURL);;
                                }
                            }
                        }
                    }
                    // This is the last line of the loop
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // Return only the unique icon urls
        //
        return iconURLStrings;
    }

    public void cancelImmediatelyWithError(String error)
    {
        // TODO
/*
        // Creating the database failed for some reason, so clean up and change the state back to available
        //
        state = MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateCanceling;
        [self notifyDelegateOfStateChange];

        if([_delegate respondsToSelector:@selector(offlineMapDownloader:didCompleteOfflineMapDatabase:withError:)])
        {
            dispatch_async(dispatch_get_main_queue(), ^(void){
                    [_delegate offlineMapDownloader:self didCompleteOfflineMapDatabase:nil withError:error];
            });
        }

        [_dataSession invalidateAndCancel];
        [_sqliteQueue cancelAllOperations];

        [_sqliteQueue addOperationWithBlock:^{
        [self setUpNewDataSession];
        _totalFilesWritten = 0;
        _totalFilesExpectedToWrite = 0;

        [[NSFileManager defaultManager] removeItemAtPath:_partialDatabasePath error:nil];

        state = MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateAvailable;
        [self notifyDelegateOfStateChange];
    }];
*/
    }

/*
    API: Control an in-progress offline map download
*/

    public void cancel()
    {
        if (state != MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateCanceling && state != MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateAvailable)
        {
            // TODO
/*
            // Stop a download job and discard the associated files
            //
            [_backgroundWorkQueue addOperationWithBlock:^{
            _state = MBXOfflineMapDownloaderStateCanceling;
            [self notifyDelegateOfStateChange];

            [_dataSession invalidateAndCancel];
            [_sqliteQueue cancelAllOperations];

            [_sqliteQueue addOperationWithBlock:^{
                [self setUpNewDataSession];
                _totalFilesWritten = 0;
                _totalFilesExpectedToWrite = 0;
                [[NSFileManager defaultManager] removeItemAtPath:_partialDatabasePath error:nil];

                if([_delegate respondsToSelector:@selector(offlineMapDownloader:didCompleteOfflineMapDatabase:withError:)])
                {
                    NSError *canceled = [NSError mbx_errorWithCode:MBXMapKitErrorCodeDownloadingCanceled reason:@"The download job was canceled" description:@"Download canceled"];
                    dispatch_async(dispatch_get_main_queue(), ^(void){
                            [_delegate offlineMapDownloader:self didCompleteOfflineMapDatabase:nil withError:canceled];
                    });
                }

                _state = MBXOfflineMapDownloaderStateAvailable;
                [self notifyDelegateOfStateChange];
            }];

            }
*/
        }
    }

    public void resume()
    {
        if (state != MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateSuspended) {
            return;
        }

        // TODO
/*
        // Resume a previously suspended download job
        //
        [_backgroundWorkQueue addOperationWithBlock:^{
            _state = MBXOfflineMapDownloaderStateRunning;
            [self startDownloading];
            [self notifyDelegateOfStateChange];
        }];
*/
    }


    public void suspend()
    {
        if (state == MBXOfflineMapDownloaderState.MBXOfflineMapDownloaderStateRunning)
        {
            // TODO
/*
            // Stop a download job, preserving the necessary state to resume later
            //
            [_backgroundWorkQueue addOperationWithBlock:^{
                [_sqliteQueue cancelAllOperations];
                _state = MBXOfflineMapDownloaderStateSuspended;
                _activeDataSessionTasks = 0;
                [self notifyDelegateOfStateChange];
            }];
*/
        }
    }


/*
    API: Access or delete completed offline map databases on disk
*/

    public ArrayList<OfflineMapDatabase> getMutableOfflineMapDatabases() {
        // Return an array with offline map database objects representing each of the *complete* map databases on disk
        return mutableOfflineMapDatabases;
    }

    public void removeOfflineMapDatabase(OfflineMapDatabase offlineMapDatabase)
    {
        // Mark the offline map object as invalid in case there are any references to it still floating around
        //
        offlineMapDatabase.invalidate();


        // Remove the offline map object from the array and delete it's backing database
        //
        mutableOfflineMapDatabases.remove(offlineMapDatabase);
    }

    public void removeOfflineMapDatabaseWithID(String uniqueID)
    {
        for (OfflineMapDatabase database : getMutableOfflineMapDatabases())
        {
            if (database.getUniqueID().equals(uniqueID))
            {
                removeOfflineMapDatabase(database);
                return;
            }
        }
    }
}
