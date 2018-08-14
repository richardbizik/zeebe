import {parseQueryString, getParentFilter, getPayload} from './service';

describe('Instances service', () => {
  describe('parseQueryString', () => {
    it('should return a empty object for invalid querys string', () => {
      const invalidInputA = '?filter={"active":truef,"incidents":true}';
      const invalidInputB = '?filter=';
      const invalidInputC = '';

      expect(parseQueryString(invalidInputA)).toEqual({});
      expect(parseQueryString(invalidInputB)).toEqual({});
      expect(parseQueryString(invalidInputC)).toEqual({});
    });

    it('should return an object for valid query strings', () => {
      const input = '?filter={"a":true,"b":true,"c":"X"}';
      const output = {filter: {a: true, b: true, c: 'X'}};

      expect(parseQueryString(input)).toEqual(output);
    });

    it('should support query strings with more params', () => {
      const input = '?filter={"a":true,"b":true,"c":"X"}&extra={"extra": true}';
      const output = {
        filter: {a: true, b: true, c: 'X'},
        extra: {extra: true}
      };

      expect(parseQueryString(input)).toEqual(output);
    });
  });
});

describe('Selection services', () => {
  let filter;
  let state;

  it('should return parent filter', () => {
    //when
    filter = {incidents: true};
    //then
    expect(getParentFilter(filter)).toEqual({running: true});

    //when
    filter = {completed: true};
    //then
    expect(getParentFilter(filter)).toEqual({finished: true});
  });

  it('should return payload for create new Selection', () => {
    //when
    state = {
      selection: {ids: [], excludeIds: []},
      selections: [],
      filter: {incidents: true}
    };

    //then
    expect(getPayload({state})).toMatchSnapshot();
  });
});
