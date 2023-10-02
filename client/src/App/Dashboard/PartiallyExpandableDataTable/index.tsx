/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {DataTable, TableBody, TableContainer} from '@carbon/react';
import {
  Table,
  TableExpandRow,
  ExpandableTableCell,
  TableExpandedRow,
} from './styled';

type Props = {
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof DataTable>['rows'];
  className?: string;
  expandedContents?: {
    [key: string]: React.ReactElement<{tabIndex: number}>;
  };
  dataTestId?: string;
};

const PartiallyExpandableDataTable: React.FC<Props> = ({
  headers,
  rows,
  expandedContents,
  dataTestId,
}) => {
  return (
    <DataTable
      size="sm"
      headers={headers}
      rows={rows}
      render={({
        rows,
        headers,
        getTableContainerProps,
        getTableProps,
        getRowProps,
      }) => (
        <TableContainer {...getTableContainerProps()} data-testid={dataTestId}>
          <Table {...getTableProps()}>
            <TableBody>
              {rows.map((row, index) => {
                const expandedContent = expandedContents?.[row.id];

                const isExpandable =
                  expandedContent !== undefined &&
                  React.isValidElement(expandedContent);

                return (
                  <React.Fragment key={row.id}>
                    <TableExpandRow
                      {...getRowProps({row})}
                      data-testid={`${dataTestId}-${index}`}
                      $isExpandable={isExpandable}
                    >
                      {row.cells.map((cell) => (
                        <ExpandableTableCell key={cell.id}>
                          {cell.value}
                        </ExpandableTableCell>
                      ))}
                    </TableExpandRow>

                    {isExpandable && (
                      <TableExpandedRow colSpan={headers.length + 1}>
                        {React.cloneElement(expandedContent, {
                          tabIndex: row.isExpanded ? 0 : -1,
                        })}
                      </TableExpandedRow>
                    )}
                  </React.Fragment>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    />
  );
};

export {PartiallyExpandableDataTable};