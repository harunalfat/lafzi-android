package org.lafzi.android.filters;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.widget.SearchView;
import android.view.View;
import android.widget.Filter;
import android.widget.TextView;

import org.lafzi.android.R;
import org.lafzi.android.adapters.AyatAdapter;
import org.lafzi.android.helpers.database.DbHelper;
import org.lafzi.android.helpers.database.dao.AyatQuranDao;
import org.lafzi.android.helpers.database.dao.AyatQuranDaoFactory;
import org.lafzi.android.helpers.database.dao.IndexDao;
import org.lafzi.android.helpers.database.dao.IndexDaoFactory;
import org.lafzi.android.helpers.database.dao.MappingPosisiDao;
import org.lafzi.android.helpers.database.dao.MappingPosisiDaoFactory;
import org.lafzi.android.helpers.preferences.Preferences;
import org.lafzi.android.models.AyatQuran;
import org.lafzi.android.models.FoundDocument;
import org.lafzi.android.utils.HighlightUtil;
import org.lafzi.android.utils.QueryUtil;
import org.lafzi.android.utils.SearchUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by alfat on 21/04/17.
 */

public class AyatAdapterFilter extends Filter {

    private final AyatQuranDao ayatQuranDao;
    private final IndexDao indexDao;
    private final MappingPosisiDao posisiDao;

    private final Context context;
    private final AyatAdapter adapter;

    private int maxScore;

    public AyatAdapterFilter(final Context context, final AyatAdapter adapter){
        final DbHelper dbHelper = DbHelper.getInstance(context);
        final SQLiteDatabase db = dbHelper.getReadableDatabase();

        ayatQuranDao = AyatQuranDaoFactory.createAyatDao(db);
        indexDao = IndexDaoFactory.createIndexDao(db);
        posisiDao = MappingPosisiDaoFactory.createMappingPosisiDao(db);

        this.adapter = adapter;
        this.context = context;
    }

    @Override
    protected FilterResults performFiltering(CharSequence constraint) {

        Map<Integer, FoundDocument> matchedDocs;
        double threshold = 0.9;
        final boolean isVocal = new Preferences(context).isVocal();

        final String queryFinal = QueryUtil.normalizeQuery(constraint.toString(), isVocal);
        maxScore = queryFinal.length() - 2;

        do {
            matchedDocs = SearchUtil.search(
                    queryFinal,
                    isVocal,
                    true,
                    true,
                    threshold,
                    indexDao);
            threshold -= 0.1;
        } while ((matchedDocs.size() < 1) && (threshold >= 0.7));


        List<FoundDocument> matchedDocsValue;
        List<AyatQuran> ayatQurans = new ArrayList<>();

        if (matchedDocs.size() > 0){

            HighlightUtil.highlightPositions(matchedDocs, isVocal, ayatQuranDao, posisiDao);
            matchedDocsValue = getMatchedDocsValues(matchedDocs);

            Collections.sort(matchedDocsValue, new Comparator<FoundDocument>() {
                @Override
                public int compare(FoundDocument o1, FoundDocument o2) {
                    if (o1.getScore() == o2.getScore()){
                        return o1.getAyatQuranId() - o2.getAyatQuranId();
                    }

                    return o1.getScore() < o2.getScore() ? 1 : -1;
                }
            });

            ayatQurans = getMatchedAyats(matchedDocsValue);
        }

        final FilterResults results = new FilterResults();
        results.values = ayatQurans;
        results.count = ayatQurans.size();

        return results;
    }

    private List<FoundDocument> getMatchedDocsValues(final Map<Integer, FoundDocument> matchedDocs){
        final List<FoundDocument> values = new LinkedList<>();
        for (Map.Entry<Integer, FoundDocument> entry : matchedDocs.entrySet()){
            values.add(entry.getValue());
        }

        return values;
    }

    @Override
    protected void publishResults(CharSequence constraint, FilterResults results) {
        adapter.clear();
        SearchView searchView = (SearchView)((Activity) context).findViewById(R.id.search);
        searchView.clearFocus();

        final TextView resultCounter = (TextView)((Activity) context).findViewById(R.id.result_counter);
        if (results.count > 0) {
            adapter.addAll((List<AyatQuran>)results.values);

            resultCounter.setText(context.getString(R.string.search_result_count, results.count));
            resultCounter.setVisibility(View.VISIBLE);
        } else
            resultCounter.setVisibility(View.GONE);
    }

    private List<AyatQuran> getMatchedAyats(final List<FoundDocument> foundDocuments){

        final List<AyatQuran> ayatQurans = new LinkedList<>();
        for (FoundDocument document : foundDocuments){
            final double relevance = Math.min(Math.floor(document.getScore() / maxScore * 100), 100);

            final AyatQuran ayatQuran = document.getAyatQuran();
            ayatQuran.relevance = relevance;
            ayatQuran.highlightPositions = document.getHighlightPosition();

            ayatQurans.add(ayatQuran);
        }

        return ayatQurans;
    }
}