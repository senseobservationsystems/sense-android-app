package nl.sense_os.app.appwidget;

import nl.sense_os.app.R;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

public class SenseWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ComponentName provider = new ComponentName(context, SenseWidgetProvider.class);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget);
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        mgr.updateAppWidget(provider, views);
    }
}
