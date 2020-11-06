import React from "react";
import { connect } from "react-redux";
import { t } from "ttag";

import { DatabaseSchemaAndTableDataSelector } from "metabase/query_builder/components/DataSelector";
import { NotebookCell, NotebookCellItem } from "../NotebookCell";

import { getDatabasesList } from "metabase/query_builder/selectors";

function DataStep({ color, query, databases, updateQuery }) {
  const table = query.table();
  return (
    <NotebookCell color={color}>
      <DatabaseSchemaAndTableDataSelector
        databaseQuery={{ saved: true }}
        selectedDatabaseId={query.databaseId()}
        selectedTableId={query.tableId()}
        setSourceTableFn={tableId =>
          query
            .setTableId(tableId)
            .setDefaultQuery()
            .update(updateQuery)
        }
        isInitiallyOpen={!query.tableId()}
        triggerElement={
          !query.tableId() ? (
            <NotebookCellItem color={color} inactive>
              {t`Pick your starting data`}
            </NotebookCellItem>
          ) : (
            <NotebookCellItem color={color} icon="table2">
              {table && table.displayName()}
            </NotebookCellItem>
          )
        }
      />
      {table && query.isRaw() && (
        <DataFieldsPicker
          className="ml-auto mb1 text-bold"
          query={query}
          updateQuery={updateQuery}
        />
      )}
    </NotebookCell>
  );
}

export default connect(state => ({ databases: getDatabasesList(state) }))(
  DataStep,
);

import FieldsPicker from "./FieldsPicker";

class DataFieldsPicker extends React.Component {
  constructor(props) {
    super(props);
    this.state = {empty: true};
  }

  render() {
    const dimensions = this.props.query.tableDimensions();
    const selectedDimensions = this.props.query.columnDimensions();
    const selected = new Set(selectedDimensions.map(d => d.key()));
    const fields = this.props.query.fields();

    console.log('render data step');

    return (
      <FieldsPicker
        className={this.props.className}
        dimensions={dimensions}
        selectedDimensions={this.state.empty? [] : selectedDimensions}
        isAll={!this.state.empty && (!fields || fields.length === 0)}
        isNone={this.state.empty}
        onSelectAll={() => {
          this.setState(() => ({empty: false}));
          this.props.query.clearFields().update(this.props.updateQuery);
        }}
        onSelectNone={() => this.setState(() => ({empty: true}))}
        onToggleDimension={(dimension, enable) => {
          this.setState(() => ({empty: false}));
          this.props.query
            .setFields(
              dimensions
                .filter(d => {
                  if (d === dimension) {
                    return !selected.has(d.key());
                  } else {
                    return selected.has(d.key());
                  }
                })
                .map(d => d.mbql()),
            )
            .update(this.props.updateQuery);
        }}
      />
    );
  }
}
