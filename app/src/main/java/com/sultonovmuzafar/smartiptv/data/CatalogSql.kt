package com.sultonovmuzafar.smartiptv.data

import java.util.Locale

/** Bind every user-supplied value. Search wildcard characters are treated literally. */
internal data class CatalogSql(val where: String,val args: Array<String>) {
    companion object {
        fun forFilter(filter: CatalogFilter): CatalogSql {
            val clauses=mutableListOf(if(filter.tab==2) "1=1" else "c.parent=''")
            val args=mutableListOf<String>()
            if(filter.source.isNotEmpty()) { clauses+="c.source=?";args+=filter.source }
            if(filter.group.isNotEmpty()) { clauses+="c.group_name=?";args+=filter.group }
            if(filter.tab==0) {
                if(filter.kind=="VIDEO") clauses+="c.kind IN ('VIDEO','IMAGE')"
                else { clauses+="c.kind=?";args+=filter.kind }
            }
            if(filter.tab==1) clauses+="h.favorite=1"
            if(filter.tab==2) clauses+="h.last_played>0"
            val text=filter.search.trim().lowercase(Locale.ROOT)
            if(text.isNotEmpty()) {
                clauses+="c.search_text LIKE ? ESCAPE '\\'"
                args+="%"+text.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%"
            }
            return CatalogSql(clauses.joinToString(" AND "),args.toTypedArray())
        }
    }
}
